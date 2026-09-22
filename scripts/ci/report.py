#!/usr/bin/env python3
"""CI report helper for the catalog-service workflow.

Reads the XML/SARIF reports that the Maven plugins and CodeQL already produce,
writes a Markdown section to the GitHub step summary, emits file/line
annotations, publishes a one-line ``status`` job output, and decides the final
``verify`` result from the outcomes of the required jobs.

Standard library only: it must run on a bare ``ubuntu-latest`` runner with no
``pip install``.  Every input comes from a build tool or from the workflow, but
filenames and messages are still treated as untrusted and escaped for both
GitHub workflow commands and Markdown.

Exit codes: 0 = gate satisfied, 1 = gate failed (violations, missing data,
threshold), 2 = the helper itself could not render (bug or malformed input).
A non-zero exit never *hides* a failure: the Maven step that owns the gate
has already failed the job by then; the helper only adds the explanation.
"""

from __future__ import annotations

import argparse
import glob
import json
import os
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path

MAX_ROWS = 40
MAX_MESSAGE = 200
PARTIAL_NOTE = "coverage collected during failed tests may be partial"


# --------------------------------------------------------------------------- #
# Escaping and GitHub output helpers
# --------------------------------------------------------------------------- #

_CONTROL = re.compile(r"[\x00-\x08\x0b-\x1f\x7f]")


def clean(text: object, limit: int = MAX_MESSAGE) -> str:
    """Collapse whitespace, drop control characters, cap the length."""
    value = " ".join(str(text if text is not None else "").split())
    value = _CONTROL.sub("", value)
    if len(value) > limit:
        value = value[: limit - 1] + "…"
    return value


def md(text: object, limit: int = MAX_MESSAGE) -> str:
    """Escape text for use inside a Markdown table cell."""
    value = clean(text, limit)
    value = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    for ch in "\\`*[]|":  # intraword _ is not emphasis in GFM, so names stay readable
        value = value.replace(ch, "\\" + ch)
    return value


def _cmd_data(text: str) -> str:
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def _cmd_prop(text: str) -> str:
    return _cmd_data(text).replace(":", "%3A").replace(",", "%2C")


def annotate(level: str, message: str, *, file: str | None = None, line: int | None = None,
             title: str | None = None) -> None:
    """Emit a GitHub workflow command (``::error file=...::msg``)."""
    props = []
    if file:
        props.append(f"file={_cmd_prop(clean(file, 500))}")
    if line:
        props.append(f"line={int(line)}")
    if title:
        props.append(f"title={_cmd_prop(clean(title, 100))}")
    joined = (" " + ",".join(props)) if props else ""
    print(f"::{level}{joined}::{_cmd_data(clean(message, 1000))}")


def _append(env_var: str, content: str) -> None:
    path = os.environ.get(env_var)
    if not path:
        return
    with open(path, "a", encoding="utf-8") as handle:
        handle.write(content)


def write_summary(markdown: str) -> None:
    """Append to ``$GITHUB_STEP_SUMMARY``; print to stdout as well for the log."""
    print(markdown)
    _append("GITHUB_STEP_SUMMARY", markdown + "\n")


def set_output(name: str, value: str) -> None:
    """Publish a single-line job output through ``$GITHUB_OUTPUT``."""
    _append("GITHUB_OUTPUT", f"{name}={clean(value, 500)}\n")


def relative(path: str, root: Path) -> str:
    try:
        return str(Path(path).resolve().relative_to(root.resolve()))
    except (ValueError, OSError):
        return path


# --------------------------------------------------------------------------- #
# Safe XML loading
# --------------------------------------------------------------------------- #

class ReportError(Exception):
    """A report exists but cannot be interpreted."""


def load_xml(path: Path) -> ET.Element:
    """Parse XML with entity declarations refused.

    JaCoCo's report carries a harmless ``<!DOCTYPE ... "report.dtd">`` so a
    DOCTYPE itself is allowed; expat never fetches an external DTD unless a
    handler is installed, and none is.  What *is* refused is any ``<!ENTITY``
    declaration, the vector for external-entity (XXE) and entity-expansion
    attacks: no build report needs one."""
    raw = path.read_bytes()
    if not raw.strip():
        raise ReportError(f"{path} is empty")
    if b"<!entity" in raw.lower():
        raise ReportError(f"{path} declares an XML entity, which is not allowed")
    try:
        return ET.fromstring(raw)
    except ET.ParseError as exc:
        raise ReportError(f"{path} is not well-formed XML: {exc}") from exc


def strip_ns(tag: str) -> str:
    return tag.split("}", 1)[1] if "}" in tag else tag


# --------------------------------------------------------------------------- #
# Result model shared by every sub-command
# --------------------------------------------------------------------------- #

@dataclass
class Outcome:
    name: str
    ok: bool
    status: str                     # one line, becomes the job output
    lines: list[str] = field(default_factory=list)  # Markdown body

    def render(self) -> str:
        verdict = "PASS" if self.ok else "FAIL"
        body = "\n".join(self.lines)
        return f"### {self.name}: {verdict}\n\n{md(self.status, 500)}\n\n{body}".rstrip() + "\n"


def finish(outcome: Outcome, output_name: str) -> int:
    write_summary(outcome.render())
    set_output(output_name, ("PASS" if outcome.ok else "FAIL") + " — " + outcome.status)
    return 0 if outcome.ok else 1


def gate_failed_before_report(gate_outcome: str, path: Path, tool: str) -> Outcome | None:
    """When the Maven step already failed *and* left no report, explain that
    honestly instead of inventing a second failure reason."""
    if gate_outcome != "success" and not path.exists():
        return Outcome(tool, False,
                       f"the build step ended with '{gate_outcome}' before producing "
                       f"{path.name} (compilation or setup error; open the job log)")
    return None


def table(header: list[str], rows: list[list[str]]) -> list[str]:
    lines = ["| " + " | ".join(header) + " |", "|" + "---|" * len(header)]
    for row in rows[:MAX_ROWS]:
        lines.append("| " + " | ".join(row) + " |")
    if len(rows) > MAX_ROWS:
        lines.append("")
        lines.append(f"Showing {MAX_ROWS} of {len(rows)} rows; the full report is in the job artifact.")
    return lines


# --------------------------------------------------------------------------- #
# Checkstyle
# --------------------------------------------------------------------------- #

def cmd_checkstyle(args: argparse.Namespace) -> int:
    path = Path(args.xml)
    root = Path(args.root)
    early = gate_failed_before_report(args.gate_outcome, path, "Checkstyle")
    if early:
        return finish(early, "status")
    if not path.exists():
        return finish(Outcome("Checkstyle", False, f"{path} was not produced although the step succeeded"), "status")
    doc = load_xml(path)
    rows: list[list[str]] = []
    for file_el in doc.iter("file"):
        name = relative(file_el.get("name", "?"), root)
        for err in file_el.findall("error"):
            line = int(err.get("line") or 0)
            rule = (err.get("source") or "").rsplit(".", 1)[-1]
            message = err.get("message", "")
            rows.append([md(name), str(line), md(rule), md(message)])
            annotate("error", message, file=name, line=line, title=f"Checkstyle: {rule}")
    if rows:
        outcome = Outcome("Checkstyle", False, f"{len(rows)} violation(s)",
                          table(["File", "Line", "Rule", "Message"], rows))
    elif args.gate_outcome != "success":
        outcome = Outcome("Checkstyle", False,
                          f"the Maven step ended with '{args.gate_outcome}' but the report lists no "
                          "violation; open the job log")
    else:
        outcome = Outcome("Checkstyle", True, "no violations")
    return finish(outcome, "status")


# --------------------------------------------------------------------------- #
# PMD
# --------------------------------------------------------------------------- #

def cmd_pmd(args: argparse.Namespace) -> int:
    path = Path(args.xml)
    root = Path(args.root)
    early = gate_failed_before_report(args.gate_outcome, path, "PMD")
    if early:
        return finish(early, "status")
    if not path.exists():
        return finish(Outcome("PMD", False, f"{path} was not produced although the step succeeded"), "status")
    doc = load_xml(path)
    rows: list[list[str]] = []
    errors: list[str] = []
    for el in doc.iter():
        tag = strip_ns(el.tag)
        if tag == "file":
            name = relative(el.get("name", "?"), root)
            for v in el:
                if strip_ns(v.tag) != "violation":
                    continue
                line = int(v.get("beginline") or 0)
                rule = v.get("rule", "?")
                message = (v.text or "").strip()
                link = v.get("externalInfoUrl")
                rule_cell = f"[{md(rule)}]({link})" if link and link.startswith("https://") else md(rule)
                rows.append([md(name), str(line), rule_cell, md(message)])
                annotate("error", message, file=name, line=line, title=f"PMD: {rule}")
        elif tag in ("error", "configerror"):
            errors.append(clean(el.get("msg") or el.get("filename") or "processing error"))
    for err in errors:
        annotate("error", err, title="PMD processing error")
    if rows or errors:
        status = f"{len(rows)} violation(s)" + (f", {len(errors)} processing error(s)" if errors else "")
        body = table(["File", "Line", "Rule", "Message"], rows)
        if errors:
            body += [""] + [f"- processing error: {md(e)}" for e in errors]
        outcome = Outcome("PMD", False, status, body)
    elif args.gate_outcome != "success":
        outcome = Outcome("PMD", False,
                          f"the Maven step ended with '{args.gate_outcome}' but the report lists no "
                          "violation; open the job log")
    else:
        outcome = Outcome("PMD", True, "no violations")
    return finish(outcome, "status")


# --------------------------------------------------------------------------- #
# Surefire test reports
# --------------------------------------------------------------------------- #

def cmd_tests(args: argparse.Namespace) -> int:
    directory = Path(args.reports_dir)
    files = sorted(directory.glob("TEST-*.xml")) if directory.is_dir() else []
    if not files:
        status = (f"no test reports in {directory}; the build step ended with '{args.gate_outcome}' "
                  "before running tests" if args.gate_outcome != "success"
                  else f"no test reports in {directory} although the step succeeded")
        return finish(Outcome("Tests", False, status), "status")
    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    rows: list[list[str]] = []
    for file in files:
        suite = load_xml(file)
        for key in totals:
            totals[key] += int(suite.get(key) or 0)
        for case in suite.iter("testcase"):
            for problem in case:
                kind = strip_ns(problem.tag)
                if kind not in ("failure", "error"):
                    continue
                name = f"{case.get('classname', '?')}.{case.get('name', '?')}"
                message = problem.get("message") or (problem.text or "").strip().splitlines()[:1]
                message = message if isinstance(message, str) else (message[0] if message else "")
                rows.append([md(name), kind, md(message)])
                annotate("error", f"{name}: {message}", title=f"Test {kind}")
    problems = totals["failures"] + totals["errors"]
    status = (f"{totals['tests']} run, {totals['failures']} failed, {totals['errors']} errors, "
              f"{totals['skipped']} skipped")
    if totals["tests"] == 0:
        outcome = Outcome("Tests", False, "reports exist but contain zero tests")
    elif problems:
        outcome = Outcome("Tests", False, status, table(["Test", "Kind", "Message"], rows))
    elif args.gate_outcome != "success":
        outcome = Outcome("Tests", False,
                          f"{status}; the Maven step ended with '{args.gate_outcome}' anyway "
                          "(packaging or plugin error; open the job log)")
    else:
        outcome = Outcome("Tests", True, status)
    return finish(outcome, "status")


# --------------------------------------------------------------------------- #
# JaCoCo coverage
# --------------------------------------------------------------------------- #

def read_minimum(pom: Path) -> float:
    """Read the INSTRUCTION COVEREDRATIO minimum of the JaCoCo ``check``
    execution so the summary and the gate can never disagree."""
    doc = load_xml(pom)
    for execution in doc.iter():
        if strip_ns(execution.tag) != "execution":
            continue
        ident = next((c.text for c in execution if strip_ns(c.tag) == "id"), None)
        if ident != "check":
            continue
        counter_value = minimum = None
        for el in execution.iter():
            tag = strip_ns(el.tag)
            if tag == "limit":
                counter_value = minimum = None
                for c in el:
                    if strip_ns(c.tag) == "counter":
                        counter_value = (c.text or "").strip()
                    elif strip_ns(c.tag) == "minimum":
                        minimum = (c.text or "").strip()
                if counter_value == "INSTRUCTION" and minimum:
                    return float(minimum)
    raise ReportError(f"no INSTRUCTION minimum found in the JaCoCo 'check' execution of {pom}")


def cmd_coverage(args: argparse.Namespace) -> int:
    xml_path = Path(args.xml)
    exec_path = Path(args.exec)
    minimum = read_minimum(Path(args.pom))
    partial = args.tests_outcome != "success"
    if not exec_path.exists() or exec_path.stat().st_size == 0:
        status = ("no JaCoCo execution data (target/jacoco.exec); "
                  + ("tests did not run to completion" if partial else
                     "the agent did not record although tests passed"))
        annotate("error", status, title="Coverage data missing")
        return finish(Outcome("Coverage", False, status), "status")
    if not xml_path.exists():
        status = f"{xml_path} was not generated; coverage cannot be evaluated"
        annotate("error", status, title="Coverage report missing")
        return finish(Outcome("Coverage", False, status), "status")
    report = load_xml(xml_path)
    counter = next((c for c in report if strip_ns(c.tag) == "counter"
                    and c.get("type") == "INSTRUCTION"), None)
    if counter is None:
        raise ReportError(f"{xml_path} has no bundle-level INSTRUCTION counter")
    covered = int(counter.get("covered") or 0)
    missed = int(counter.get("missed") or 0)
    total = covered + missed
    if total == 0:
        return finish(Outcome("Coverage", False, "report counts zero instructions"), "status")
    ratio = covered / total
    status = f"{ratio * 100:.1f}% ({covered}/{total} instructions) / required {minimum * 100:.1f}%"
    if partial:
        status += f" — {PARTIAL_NOTE}"
    ok = ratio >= minimum and not partial and args.gate_outcome == "success"
    lines: list[str] = []
    if ratio < minimum:
        annotate("error", status, title="Coverage below threshold")
        lines.append(f"- coverage is below the {minimum * 100:.1f}% instruction threshold")
    if partial:
        lines.append("- tests did not succeed, so this coverage is informational only")
    elif args.gate_outcome != "success":
        lines.append(f"- the `jacoco:check@check` step ended with '{args.gate_outcome}'")
    lines.append("- the HTML report is in the coverage artifact (`index.html`)")
    return finish(Outcome("Coverage", ok, status, lines), "status")


# --------------------------------------------------------------------------- #
# SARIF (CodeQL)
# --------------------------------------------------------------------------- #

def _rule_for(result: dict, run: dict) -> dict:
    driver = run.get("tool", {}).get("driver", {})
    extensions = run.get("tool", {}).get("extensions", []) or []
    ref = result.get("rule") or {}
    component = driver
    if isinstance(ref.get("toolComponent"), dict) and "index" in ref["toolComponent"]:
        idx = ref["toolComponent"]["index"]
        if 0 <= idx < len(extensions):
            component = extensions[idx]
    rules = component.get("rules", []) or []
    index = ref.get("index", result.get("ruleIndex"))
    if isinstance(index, int) and 0 <= index < len(rules):
        return rules[index]
    rule_id = ref.get("id") or result.get("ruleId")
    for comp in [driver, *extensions]:
        for rule in comp.get("rules", []) or []:
            if rule.get("id") == rule_id:
                return rule
    return {"id": rule_id or "?"}


def _severity(result: dict, rule: dict) -> tuple[float | None, str]:
    props = rule.get("properties", {}) or {}
    raw = props.get("security-severity")
    if raw not in (None, ""):
        try:
            return float(raw), "security-severity"
        except (TypeError, ValueError):
            pass
    level = result.get("level") or (rule.get("defaultConfiguration", {}) or {}).get("level") or "warning"
    return None, str(level)


def _location(result: dict) -> tuple[str, int]:
    for loc in result.get("locations", []) or []:
        phys = loc.get("physicalLocation", {}) or {}
        uri = (phys.get("artifactLocation", {}) or {}).get("uri", "")
        line = (phys.get("region", {}) or {}).get("startLine", 0)
        if uri:
            return uri, int(line or 0)
    return "?", 0


def cmd_sarif(args: argparse.Namespace) -> int:
    threshold = float(args.min_severity)
    files = sorted(glob.glob(os.path.join(args.sarif_dir, "*.sarif")))
    if args.analyze_outcome != "success":
        status = f"CodeQL analysis ended with '{args.analyze_outcome}'"
        if files:
            status += "; a SARIF file exists but a failed scan is never trusted as clean"
        annotate("error", status, title="Security scan failed")
        return finish(Outcome("Security", False, status), "status")
    if not files:
        status = f"no SARIF output in {args.sarif_dir} although the analysis step succeeded"
        annotate("error", status, title="Security report missing")
        return finish(Outcome("Security", False, status), "status")
    try:
        with open(files[0], encoding="utf-8") as handle:
            sarif = json.load(handle)
    except (OSError, ValueError) as exc:
        raise ReportError(f"{files[0]} is not valid SARIF JSON: {exc}") from exc
    runs = sarif.get("runs") if isinstance(sarif, dict) else None
    if not isinstance(runs, list) or not runs:
        raise ReportError(f"{files[0]} contains no runs")
    run = next((r for r in runs if (r.get("tool", {}).get("driver", {}) or {}).get("name") == "CodeQL"), None)
    if run is None:
        raise ReportError(f"{files[0]} contains no CodeQL run")
    automation = (run.get("automationDetails", {}) or {}).get("id", "")
    if args.category and not automation.startswith(args.category):
        raise ReportError(f"expected analysis category '{args.category}', found '{automation or 'none'}'")
    for inv in run.get("invocations", []) or []:
        if inv.get("executionSuccessful") is False:
            status = "CodeQL reported an unsuccessful invocation"
            annotate("error", status, title="Security scan error")
            return finish(Outcome("Security", False, status), "status")
    blocking: list[list[str]] = []
    informational: list[list[str]] = []
    for result in run.get("results", []) or []:
        rule = _rule_for(result, run)
        score, basis = _severity(result, rule)
        uri, line = _location(result)
        message = (result.get("message", {}) or {}).get("text", "")
        help_uri = rule.get("helpUri", "")
        rule_id = rule.get("id", "?")
        rule_cell = f"[{md(rule_id)}]({help_uri})" if help_uri.startswith("https://") else md(rule_id)
        severity = f"{score:.1f}" if score is not None else f"level={basis}"
        row = [rule_cell, f"{md(uri)}:{line}", severity, md(message)]
        is_blocking = (score is not None and score >= threshold) or (score is None and basis == "error")
        if is_blocking:
            blocking.append(row)
            annotate("error", f"{rule_id}: {message}", file=uri, line=line, title=f"Security {severity}")
        else:
            informational.append(row)
            annotate("warning", f"{rule_id}: {message}", file=uri, line=line, title=f"Security {severity}")
    lines: list[str] = []
    if blocking:
        lines += ["**Blocking findings**", ""] + table(["Rule", "Location", "Severity", "Message"], blocking) + [""]
    if informational:
        lines += ["**Lower-severity findings (not blocking)**", ""] + \
            table(["Rule", "Location", "Severity", "Message"], informational) + [""]
    lines.append(f"- policy: security-severity ≥ {threshold:.1f} blocks; findings without a "
                 "security-severity block when their SARIF level is `error`")
    lines.append(f"- upload to GitHub code scanning: {md(args.upload_outcome)}")
    status = (f"{len(blocking)} blocking finding(s) at severity ≥ {threshold:.1f}, "
              f"{len(informational)} lower-severity")
    return finish(Outcome("Security", not blocking, status, lines), "status")


# --------------------------------------------------------------------------- #
# Final decision
# --------------------------------------------------------------------------- #

VALID_RESULTS = {"success", "failure", "cancelled", "skipped"}


def decide(changes_result: str, relevant: str, jobs: dict[str, str]) -> tuple[bool, str]:
    """The decision table from the plan.  ``jobs`` maps job name -> result."""
    if changes_result != "success":
        return False, f"change detection ended with '{changes_result}'"
    if relevant not in ("true", "false"):
        return False, f"change detection produced an invalid output '{relevant}'"
    bad = {name: res for name, res in jobs.items() if res not in VALID_RESULTS}
    if bad:
        return False, "unknown job result(s): " + ", ".join(f"{k}={v}" for k, v in bad.items())
    if relevant == "false":
        unexpected = {n: r for n, r in jobs.items() if r not in ("skipped", "success")}
        if unexpected:
            return False, "no relevant change, yet " + ", ".join(f"{k}={v}" for k, v in unexpected.items())
        return True, "catalog-service untouched, required checks intentionally skipped"
    failing = {n: r for n, r in jobs.items() if r != "success"}
    if failing:
        return False, ", ".join(f"{k}={v}" for k, v in failing.items())
    return True, "all required checks passed"


def cmd_final(args: argparse.Namespace) -> int:
    jobs: dict[str, str] = {}
    for spec in args.job:
        name, _, result = spec.partition("=")
        jobs[name] = result.strip()
    ok, reason = decide(args.changes_result, args.relevant, jobs)
    rows = []
    for spec in args.line:
        name, _, text = spec.partition("=")
        rows.append([md(name), md(text.strip() or "(no summary reported)", 300)])
    for name, result in jobs.items():
        if not any(r[0] == md(name) for r in rows):
            rows.append([md(name), md(result)])
    lines = table(["Check", "Result"], rows)
    lines.append("")
    lines.append(f"**Result: {'PASS' if ok else 'FAIL'}** — {md(reason, 500)}")
    if not ok:
        lines.append("")
        lines.append("Open the failed job or download its report artifact for details.")
        annotate("error", reason, title="verify failed")
    write_summary("## verify\n\n" + "\n".join(lines) + "\n")
    set_output("status", ("PASS" if ok else "FAIL") + " — " + reason)
    return 0 if ok else 1


# --------------------------------------------------------------------------- #
# CLI
# --------------------------------------------------------------------------- #

def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)

    def common(p: argparse.ArgumentParser) -> None:
        p.add_argument("--gate-outcome", default="success",
                       help="outcome of the Maven step that owns this gate (steps.<id>.outcome)")
        p.add_argument("--root", default=os.environ.get("GITHUB_WORKSPACE", "."),
                       help="repository root, used to relativise file paths")

    p = sub.add_parser("checkstyle"); common(p)
    p.add_argument("--xml", required=True); p.set_defaults(func=cmd_checkstyle)

    p = sub.add_parser("pmd"); common(p)
    p.add_argument("--xml", required=True); p.set_defaults(func=cmd_pmd)

    p = sub.add_parser("tests"); common(p)
    p.add_argument("--reports-dir", required=True); p.set_defaults(func=cmd_tests)

    p = sub.add_parser("coverage"); common(p)
    p.add_argument("--xml", required=True); p.add_argument("--exec", required=True)
    p.add_argument("--pom", required=True)
    p.add_argument("--tests-outcome", default="success"); p.set_defaults(func=cmd_coverage)

    p = sub.add_parser("sarif"); common(p)
    p.add_argument("--sarif-dir", required=True); p.add_argument("--min-severity", default="7.0")
    p.add_argument("--category", default="")
    p.add_argument("--analyze-outcome", default="success"); p.add_argument("--upload-outcome", default="skipped")
    p.set_defaults(func=cmd_sarif)

    p = sub.add_parser("final")
    p.add_argument("--changes-result", required=True); p.add_argument("--relevant", default="")
    p.add_argument("--job", action="append", default=[], help="name=result, repeatable")
    p.add_argument("--line", action="append", default=[], help="name=status line, repeatable")
    p.set_defaults(func=cmd_final)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        return args.func(args)
    except ReportError as exc:
        annotate("error", str(exc), title="CI report helper")
        write_summary(f"### {args.command}: report could not be rendered\n\n{md(str(exc), 500)}\n")
        set_output("status", "FAIL — report could not be rendered: " + clean(str(exc)))
        return 2


if __name__ == "__main__":
    sys.exit(main())
