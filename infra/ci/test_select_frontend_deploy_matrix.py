from __future__ import annotations

import importlib.util
from pathlib import Path
import unittest


MODULE_PATH = Path(__file__).with_name("select-frontend-deploy-matrix.py")
spec = importlib.util.spec_from_file_location("select_frontend_deploy_matrix", MODULE_PATH)
select_frontend_deploy_matrix = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(select_frontend_deploy_matrix)


class SelectFrontendDeployMatrixTest(unittest.TestCase):
    def test_stock_target_deploys_only_stock(self) -> None:
        selected = select_frontend_deploy_matrix.select_deployments("spring-stock-web")

        self.assertEqual(len(selected), 1)
        self.assertEqual(selected[0]["bucket_key"], "stock")
        self.assertEqual(selected[0]["build_script"], "build:stock")
        self.assertEqual(selected[0]["invalidation_paths"], ["/stock", "/stock/*"])

    def test_member_group_contains_only_three_member_units(self) -> None:
        selected = select_frontend_deploy_matrix.select_deployments("all-member")

        self.assertEqual(
            [item["service"] for item in selected],
            ["spring-member-web", "spring-community-web", "spring-stock-web"],
        )

    def test_all_target_has_six_unique_buckets(self) -> None:
        selected = select_frontend_deploy_matrix.select_deployments("all")

        self.assertEqual(len(selected), 6)
        self.assertEqual(len({item["bucket_key"] for item in selected}), 6)

    def test_unknown_target_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "Unsupported frontend deployment target"):
            select_frontend_deploy_matrix.select_deployments("unknown-web")


if __name__ == "__main__":
    unittest.main()
