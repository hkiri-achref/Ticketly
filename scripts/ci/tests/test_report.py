"""Tests for scripts/ci/report.py.  Run with:  python3 -m unittest discover scripts/ci/tests

Standard-library unittest so the suite runs anywhere python3 exists.  Fixtures
are hand-written copies of real plugin output (Checkstyle 14, PMD 7, Surefire
3, JaCoCo 0.8, CodeQL SARIF 2.1) with special characters added on purpose.
"""

from __future__ import annotations

import io
import os
import shutil
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path

HERE = Path(__file__).resolve().parent
FIXTURES = HERE / "fixtures"
sys.path.insert(0, str(HERE.parent))

import report  # noqa: E402


class Run:
    """Runs the helper with fake GITHUB_* files and captures everything."""

    def __init__(self, argv: list[str]):
        self.tmp = tempfile.mkdtemp()
        env = {"GITHUB_STEP_SUMMARY": f"{self.tmp}/summary.md", "GITHUB_OUTPUT": f"{self.tmp}/output.txt"}
        old = {k: os.environ.get(k) for k in env}
        os.environ.update(env)
        buffer = io.StringIO()
        try:
            with redirect_stdout(buffer):
                self.code = report.main(argv)
        finally:
            for k, v in old.items():
                if v is None:
                    os.environ.pop(k, None)
                else:
                    os.environ[k] = v
        self.stdout = buffer.getvalue()
        self.summary = Path(env["GITHUB_STEP_SUMMARY"]).read_text()
        self.output = Path(env["GITHUB_OUTPUT"]).read_text()
        shutil.rmtree(self.tmp)

    @property
    def annotations(self) -> list[str]:
        return [line for line in self.stdout.splitlines() if line.startswith("::")]


class CheckstyleTests(unittest.TestCase):
    def test_given_clean_report_when_rendered_then_pass(self):
        run = Run(["checkstyle", "--xml", str(FIXTURES / "checkstyle-clean.xml"), "--root", "/work"])
        self.assertEqual(run.code, 0)
        self.assertIn("Checkstyle: PASS", run.summary)
        self.assertIn("status=PASS", run.output)

    def test_given_violations_when_rendered_then_fail_with_escaped_rows_and_annotations(self):
        run = Run(["checkstyle", "--xml", str(FIXTURES / "checkstyle-violations.xml"),
                   "--gate-outcome", "failure", "--root", "/work"])
        self.assertEqual(run.code, 1)
        self.assertIn("2 violation(s)", run.summary)
        self.assertIn("LineLengthCheck", run.summary)
        self.assertIn("Weird\\|Name\\`.java", run.summary)          # Markdown-escaped filename
        self.assertIn("&lt;b&gt;docs&lt;/b&gt;", run.summary)      # HTML-escaped message
        self.assertNotIn("\nsecond line", run.summary)              # newline collapsed
        errors = [a for a in run.annotations if a.startswith("::error")]
        self.assertEqual(len(errors), 2)
        self.assertIn("file=catalog-service/src/main/java/com/ticketly/catalog/api/Weird|Name`.java,line=12", errors[0])
        self.assertIn("100%25,done second line", errors[0])       # workflow-command escaping
        self.assertIn("title=Checkstyle%3A LineLengthCheck", errors[0])

    def test_given_failed_step_and_no_report_when_rendered_then_honest_failure(self):
        run = Run(["checkstyle", "--xml", "/nonexistent/checkstyle-result.xml", "--gate-outcome", "failure"])
        self.assertEqual(run.code, 1)
        self.assertIn("before producing checkstyle-result.xml", run.summary)

    def test_given_successful_step_but_missing_report_when_rendered_then_fail(self):
        run = Run(["checkstyle", "--xml", "/nonexistent/checkstyle-result.xml"])
        self.assertEqual(run.code, 1)
        self.assertIn("not produced although the step succeeded", run.summary)

    def test_given_failed_step_but_clean_report_when_rendered_then_fail(self):
        run = Run(["checkstyle", "--xml", str(FIXTURES / "checkstyle-clean.xml"), "--gate-outcome", "failure"])
        self.assertEqual(run.code, 1)
        self.assertIn("lists no violation", run.summary)


class PmdTests(unittest.TestCase):
    def test_given_clean_report_when_rendered_then_pass(self):
        run = Run(["pmd", "--xml", str(FIXTURES / "pmd-clean.xml")])
        self.assertEqual(run.code, 0)
        self.assertIn("PMD: PASS", run.summary)

    def test_given_violation_and_processing_error_when_rendered_then_fail_with_links(self):
        run = Run(["pmd", "--xml", str(FIXTURES / "pmd-violations.xml"), "--gate-outcome", "failure", "--root", "/work"])
        self.assertEqual(run.code, 1)
        self.assertIn("1 violation(s), 1 processing error(s)", run.summary)
        self.assertIn("[UnusedLocalVariable](https://docs.pmd-code.org/", run.summary)
        self.assertIn("ParseException: cannot parse &amp; stuff", run.summary)
        self.assertTrue(any("line=41" in a and "PMD%3A UnusedLocalVariable" in a for a in run.annotations))


class TestsTests(unittest.TestCase):
    def test_given_passing_suites_when_rendered_then_counts(self):
        with tempfile.TemporaryDirectory() as tmp:
            shutil.copy(FIXTURES / "TEST-pass.xml", tmp)
            run = Run(["tests", "--reports-dir", tmp])
        self.assertEqual(run.code, 0)
        self.assertIn("2 run, 0 failed, 0 errors, 0 skipped", run.output)

    def test_given_failures_when_rendered_then_rows_and_annotations(self):
        with tempfile.TemporaryDirectory() as tmp:
            shutil.copy(FIXTURES / "TEST-pass.xml", tmp)
            shutil.copy(FIXTURES / "TEST-fail.xml", tmp)
            run = Run(["tests", "--reports-dir", tmp, "--gate-outcome", "failure"])
        self.assertEqual(run.code, 1)
        self.assertIn("5 run, 1 failed, 1 errors, 1 skipped", run.summary)
        self.assertIn("given_unknown_id_when_get_then_404", run.summary)
        self.assertIn("Status expected:&lt;404&gt; but was:&lt;500&gt;", run.summary)
        self.assertIn("IllegalStateException: boom", run.summary)
        self.assertEqual(len([a for a in run.annotations if a.startswith("::error")]), 2)

    def test_given_no_reports_after_build_failure_when_rendered_then_honest_failure(self):
        with tempfile.TemporaryDirectory() as tmp:
            run = Run(["tests", "--reports-dir", tmp, "--gate-outcome", "failure"])
        self.assertEqual(run.code, 1)
        self.assertIn("before running tests", run.summary)

    def test_given_no_reports_after_success_when_rendered_then_fail(self):
        with tempfile.TemporaryDirectory() as tmp:
            run = Run(["tests", "--reports-dir", tmp])
        self.assertEqual(run.code, 1)
        self.assertIn("although the step succeeded", run.summary)

    def test_given_malformed_xml_when_rendered_then_exit_2_and_visible(self):
        with tempfile.TemporaryDirectory() as tmp:
            Path(tmp, "TEST-bad.xml").write_text("<testsuite tests='1'><testcase")
            run = Run(["tests", "--reports-dir", tmp])
        self.assertEqual(run.code, 2)
        self.assertIn("not well-formed", run.summary)
        self.assertIn("status=FAIL", run.output)


class CoverageTests(unittest.TestCase):
    POM = str(FIXTURES / "pom-fragment.xml")

    def _run(self, xml: str, exec_bytes: bytes | None = b"data", **flags: str) -> Run:
        with tempfile.TemporaryDirectory() as tmp:
            exec_path = Path(tmp, "jacoco.exec")
            if exec_bytes is not None:
                exec_path.write_bytes(exec_bytes)
            argv = ["coverage", "--xml", xml, "--exec", str(exec_path), "--pom", self.POM]
            for k, v in flags.items():
                argv += [f"--{k.replace('_', '-')}", v]
            return Run(argv)

    def test_given_threshold_in_pom_when_read_then_0_90(self):
        self.assertEqual(report.read_minimum(Path(self.POM)), 0.90)

    def test_given_real_pom_when_read_then_threshold_found(self):
        self.assertEqual(report.read_minimum(HERE.parents[2] / "catalog-service" / "pom.xml"), 0.90)

    def test_given_high_coverage_when_rendered_then_pass(self):
        run = self._run(str(FIXTURES / "jacoco-high.xml"))
        self.assertEqual(run.code, 0)
        self.assertIn("96.9% (342/353 instructions) / required 90.0%", run.output)

    def test_given_low_coverage_when_rendered_then_fail(self):
        run = self._run(str(FIXTURES / "jacoco-low.xml"), gate_outcome="failure")
        self.assertEqual(run.code, 1)
        self.assertIn("86.0%", run.summary)
        self.assertTrue(any("Coverage below threshold" in a for a in run.annotations))

    def test_given_missing_exec_when_rendered_then_fail_not_false_pass(self):
        run = self._run(str(FIXTURES / "jacoco-high.xml"), exec_bytes=None)
        self.assertEqual(run.code, 1)
        self.assertIn("no JaCoCo execution data", run.summary)

    def test_given_empty_exec_when_rendered_then_fail(self):
        run = self._run(str(FIXTURES / "jacoco-high.xml"), exec_bytes=b"")
        self.assertEqual(run.code, 1)

    def test_given_missing_xml_when_rendered_then_fail(self):
        run = self._run("/nonexistent/jacoco.xml")
        self.assertEqual(run.code, 1)
        self.assertIn("not generated", run.summary)

    def test_given_failed_tests_when_rendered_then_partial_and_fail(self):
        run = self._run(str(FIXTURES / "jacoco-high.xml"), tests_outcome="failure")
        self.assertEqual(run.code, 1)
        self.assertIn(report.PARTIAL_NOTE, run.output)

    def test_given_entity_declaration_when_rendered_then_rejected(self):
        run = self._run(str(FIXTURES / "jacoco-entity.xml"))
        self.assertEqual(run.code, 2)
        self.assertIn("declares an XML entity", run.summary)


class SarifTests(unittest.TestCase):
    def _run(self, name: str, **flags: str) -> Run:
        argv = ["sarif", "--sarif-dir", str(FIXTURES / name), "--category", "/language:java-kotlin"]
        for k, v in flags.items():
            argv += [f"--{k.replace('_', '-')}", v]
        return Run(argv)

    def test_given_no_findings_when_rendered_then_pass(self):
        run = self._run("sarif-none")
        self.assertEqual(run.code, 0)
        self.assertIn("0 blocking finding(s)", run.output)

    def test_given_low_and_medium_when_rendered_then_pass_but_shown(self):
        run = self._run("sarif-low")
        self.assertEqual(run.code, 0)
        self.assertIn("2 lower-severity", run.output)
        self.assertIn("java/log-injection", run.summary)
        self.assertEqual(len([a for a in run.annotations if a.startswith("::warning")]), 2)

    def test_given_high_when_rendered_then_fail_even_if_analysis_succeeded(self):
        run = self._run("sarif-high", analyze_outcome="success")
        self.assertEqual(run.code, 1)
        self.assertIn("1 blocking finding(s)", run.output)
        self.assertIn("[java/sql-injection](https://codeql.github.com/", run.summary)
        self.assertIn("SQL \\`injection\\` 100%", run.summary)
        self.assertTrue(any(a.startswith("::error") and "line=10" in a for a in run.annotations))

    def test_given_no_severity_metadata_when_rendered_then_error_level_blocks(self):
        run = self._run("sarif-nometa")
        self.assertEqual(run.code, 1)
        self.assertIn("1 blocking finding(s)", run.output)
        self.assertIn("1 lower-severity", run.output)

    def test_given_malformed_sarif_when_rendered_then_exit_2(self):
        run = self._run("sarif-malformed")
        self.assertEqual(run.code, 2)
        self.assertIn("not valid SARIF JSON", run.summary)

    def test_given_scanner_error_when_rendered_then_fail(self):
        run = self._run("sarif-error")
        self.assertEqual(run.code, 1)
        self.assertIn("unsuccessful invocation", run.summary)

    def test_given_failed_analyze_step_when_rendered_then_fail_regardless_of_file(self):
        run = self._run("sarif-none", analyze_outcome="failure")
        self.assertEqual(run.code, 1)
        self.assertIn("never trusted as clean", run.summary)

    def test_given_missing_output_when_rendered_then_fail(self):
        with tempfile.TemporaryDirectory() as tmp:
            run = Run(["sarif", "--sarif-dir", tmp])
        self.assertEqual(run.code, 1)
        self.assertIn("no SARIF output", run.summary)

    def test_given_wrong_category_when_rendered_then_exit_2(self):
        run = self._run("sarif-wrongcat")
        self.assertEqual(run.code, 2)
        self.assertIn("expected analysis category", run.summary)

    def test_given_upload_outcome_when_rendered_then_shown(self):
        run = self._run("sarif-none", upload_outcome="failure")
        self.assertIn("upload to GitHub code scanning: failure", run.summary)


class FinalDecisionTests(unittest.TestCase):
    JOBS = ("checkstyle", "tests", "pmd", "security")

    def _decide(self, changes: str, relevant: str, *results: str) -> tuple[bool, str]:
        return report.decide(changes, relevant, dict(zip(self.JOBS, results)))

    def test_given_no_relevant_change_and_all_skipped_then_success_with_reason(self):
        ok, reason = self._decide("success", "false", "skipped", "skipped", "skipped", "skipped")
        self.assertTrue(ok)
        self.assertIn("intentionally skipped", reason)

    def test_given_relevant_change_and_all_success_then_success(self):
        self.assertTrue(self._decide("success", "true", "success", "success", "success", "success")[0])

    def test_given_relevant_change_and_one_failure_then_failure(self):
        ok, reason = self._decide("success", "true", "success", "failure", "success", "success")
        self.assertFalse(ok)
        self.assertEqual(reason, "tests=failure")

    def test_given_relevant_change_and_one_cancelled_then_failure(self):
        self.assertFalse(self._decide("success", "true", "success", "cancelled", "success", "success")[0])

    def test_given_relevant_change_and_unexpected_skip_then_failure(self):
        self.assertFalse(self._decide("success", "true", "success", "success", "skipped", "success")[0])

    def test_given_changes_failed_then_failure(self):
        self.assertFalse(self._decide("failure", "true", "success", "success", "success", "success")[0])

    def test_given_changes_cancelled_then_failure(self):
        self.assertFalse(self._decide("cancelled", "", "skipped", "skipped", "skipped", "skipped")[0])

    def test_given_missing_output_then_failure(self):
        self.assertFalse(self._decide("success", "", "skipped", "skipped", "skipped", "skipped")[0])

    def test_given_invalid_output_then_failure(self):
        self.assertFalse(self._decide("success", "yes", "success", "success", "success", "success")[0])

    def test_given_no_relevant_change_but_a_failure_then_failure(self):
        self.assertFalse(self._decide("success", "false", "skipped", "failure", "skipped", "skipped")[0])

    def test_given_unknown_job_result_then_failure(self):
        self.assertFalse(self._decide("success", "true", "success", "", "success", "success")[0])

    def test_given_cli_when_failing_then_summary_table_and_exit_1(self):
        run = Run(["final", "--changes-result", "success", "--relevant", "true",
                   "--job", "checkstyle=success", "--job", "tests=failure",
                   "--line", "Checkstyle=PASS — no violations", "--line", "Tests=FAIL — 1 failed | x"])
        self.assertEqual(run.code, 1)
        self.assertIn("**Result: FAIL**", run.summary)
        self.assertIn("1 failed \\| x", run.summary)
        self.assertIn("::error title=verify failed::tests=failure", run.stdout)

    def test_given_cli_when_docs_only_then_exit_0(self):
        run = Run(["final", "--changes-result", "success", "--relevant", "false",
                   "--job", "checkstyle=skipped", "--job", "tests=skipped"])
        self.assertEqual(run.code, 0)
        self.assertIn("intentionally skipped", run.summary)


if __name__ == "__main__":
    unittest.main()
