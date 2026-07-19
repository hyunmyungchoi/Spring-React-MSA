from __future__ import annotations

import unittest
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[2]
WORKFLOW_PATH = REPOSITORY_ROOT / ".github" / "workflows" / "aws-frontend-deploy.yml"


class AwsFrontendDeployWorkflowTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.workflow = WORKFLOW_PATH.read_text(encoding="utf-8")

    def test_exposes_each_independent_frontend_target(self) -> None:
        for target in (
            "spring-member-web",
            "spring-community-web",
            "spring-stock-web",
            "spring-admin-web",
            "spring-admin-users-web",
            "spring-admin-logs-web",
        ):
            self.assertIn(f"          - {target}\n", self.workflow)

    def test_builds_only_the_selected_workspace_and_script(self) -> None:
        self.assertIn('pnpm --filter "${{ matrix.workspace }}" run lint', self.workflow)
        self.assertIn(
            'pnpm --filter "${{ matrix.workspace }}" run "${{ matrix.build_script }}"',
            self.workflow,
        )
        self.assertNotIn("build:all", self.workflow)
        self.assertIn("PNPM_VERSION: 10.0.0", self.workflow)
        self.assertIn("pnpm install --frozen-lockfile", self.workflow)

    def test_sync_and_invalidation_are_scoped_to_the_selected_unit(self) -> None:
        self.assertIn('artifact="${GITHUB_WORKSPACE}/${{ matrix.output_dir }}"', self.workflow)
        self.assertIn(
            'bucket="${BUCKET_NAME_PREFIX}-${account_id}-frontend-${{ matrix.bucket_key }}"',
            self.workflow,
        )
        self.assertEqual(self.workflow.count('aws s3 sync "${artifact}" "s3://${bucket}"'), 2)
        self.assertIn("INVALIDATION_PATHS_JSON: ${{ toJSON(matrix.invalidation_paths) }}", self.workflow)
        self.assertIn('case "${{ matrix.distribution_group }}" in', self.workflow)

    def test_uses_oidc_and_serializes_frontend_deployments(self) -> None:
        self.assertIn("id-token: write", self.workflow)
        self.assertIn("role-to-assume: ${{ vars.AWS_FRONTEND_DEPLOY_ROLE_ARN }}", self.workflow)
        self.assertIn("group: aws-frontend-deploy", self.workflow)
        self.assertIn("cancel-in-progress: false", self.workflow)


if __name__ == "__main__":
    unittest.main()
