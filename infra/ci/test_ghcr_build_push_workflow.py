from __future__ import annotations

from pathlib import Path
import re
import unittest


WORKFLOW_PATH = Path(__file__).resolve().parents[2] / ".github/workflows/ghcr-build-push.yml"


class GhcrBuildPushWorkflowIntegrityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.workflow = WORKFLOW_PATH.read_text(encoding="utf-8")

    def test_database_migration_group_is_dispatchable(self) -> None:
        self.assertIn("- database-migrations", self.workflow)

    def test_same_source_sha_workflows_do_not_race(self) -> None:
        self.assertIn("group: ghcr-publish-${{ github.ref }}-${{ github.sha }}", self.workflow)
        self.assertIn("cancel-in-progress: false", self.workflow)

    def test_existing_sha_is_inspected_before_build(self) -> None:
        inspect_index = self.workflow.find("- name: Check immutable GHCR image")
        build_index = self.workflow.find("- name: Build and push immutable SHA image")
        self.assertGreaterEqual(inspect_index, 0)
        self.assertGreater(build_index, inspect_index)
        self.assertIn("if: steps.existing.outputs.exists == 'false'", self.workflow)

    def test_build_step_pushes_sha_only(self) -> None:
        build_blocks = re.findall(
            r"- name: Build and push immutable SHA image(?P<body>.*?)(?=\n\s+- name: Verify immutable digest)",
            self.workflow,
            flags=re.DOTALL,
        )
        self.assertEqual(len(build_blocks), 2)
        for block in build_blocks:
            self.assertIn("${{ github.sha }}", block)
            self.assertNotIn("${{ env.IMAGE_TAG }}", block)

    def test_latest_is_updated_by_registry_retag_not_rebuild(self) -> None:
        self.assertIn('crane tag "${image}@${resolved_digest}" "${IMAGE_TAG}"', self.workflow)

    def test_kubernetes_updates_use_registry_digest(self) -> None:
        self.assertIn('digest="$(crane digest', self.workflow)
        self.assertIn('--digest "${digest}"', self.workflow)
        self.assertNotIn('--sha "${{ github.sha }}"', self.workflow)


if __name__ == "__main__":
    unittest.main()
