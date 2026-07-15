from __future__ import annotations

import re
from pathlib import Path
import unittest


WORKFLOW_PATH = Path(__file__).resolve().parents[2] / ".github/workflows/ecr-build-push.yml"
APPROVED_TARGETS = [
    "all",
    "spring-member-gateway",
    "spring-admin-gateway",
    "spring-security-authorization-server",
    "spring-user-service",
    "spring-member-community-service",
    "spring-member-stock-service",
    "spring-member-bff-service",
    "spring-admin-bff-service",
]


def target_guard_facts() -> tuple[list[str], bool, int, int]:
    workflow = WORKFLOW_PATH.read_text(encoding="utf-8")
    validate_marker = "      - name: Validate backend target"
    selector_marker = "      - name: Select backend target"
    validate_index = workflow.find(validate_marker)
    selector_index = workflow.find(selector_marker)

    if validate_index < 0 or selector_index < 0 or validate_index >= selector_index:
        return [], False, validate_index, selector_index

    guard = workflow[validate_index:selector_index]
    allow_match = re.search(
        r"(?m)^\s+(all(?:\|spring-[a-z0-9-]+)+)\)\s*$",
        guard,
    )
    reject_match = re.search(
        r"(?ms)^\s+\*\)\s*$.*?^\s+exit 1\s*$",
        guard,
    )
    allowed_targets = allow_match.group(1).split("|") if allow_match else []
    return allowed_targets, reject_match is not None, validate_index, selector_index


def static_guard_exit_code(deploy_target: str) -> int:
    allowed_targets, rejects_invalid, _, _ = target_guard_facts()
    if deploy_target in allowed_targets:
        return 0
    return 1 if rejects_invalid else 0


class EcrBuildPushWorkflowTargetValidationTest(unittest.TestCase):
    def test_allowlist_is_exactly_all_plus_eight_backends(self) -> None:
        allowed_targets, _, _, _ = target_guard_facts()
        self.assertEqual(allowed_targets, APPROVED_TARGETS)

    def test_frontend_target_is_rejected(self) -> None:
        self.assertNotEqual(static_guard_exit_code("spring-member-web"), 0)

    def test_unknown_target_is_rejected(self) -> None:
        self.assertNotEqual(static_guard_exit_code("not-approved"), 0)

    def test_validation_runs_before_shared_selector(self) -> None:
        _, _, validate_index, selector_index = target_guard_facts()
        self.assertGreaterEqual(validate_index, 0)
        self.assertGreater(selector_index, validate_index)


if __name__ == "__main__":
    unittest.main()
