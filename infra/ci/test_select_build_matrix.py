from __future__ import annotations

import importlib.util
from pathlib import Path
import unittest


MODULE_PATH = Path(__file__).with_name("select-build-matrix.py")
spec = importlib.util.spec_from_file_location("select_build_matrix", MODULE_PATH)
select_build_matrix = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(select_build_matrix)


class SelectBuildMatrixTest(unittest.TestCase):
    def test_stock_service_change_selects_stock_image(self) -> None:
        self.assertEqual(
            select_build_matrix.detect_services(
                ["BackEnd/spring-member-stock-service/src/main/App.java"]
            ),
            ["spring-member-stock-service"],
        )

    def test_stock_ui_change_selects_stock_web(self) -> None:
        self.assertEqual(
            select_build_matrix.detect_services(
                ["FrontEnd/apps/member/src/stock/pages/StockEntryPage.tsx"]
            ),
            ["spring-stock-web"],
        )

    def test_shared_frontend_lock_change_selects_all_frontend_images(self) -> None:
        self.assertEqual(
            select_build_matrix.detect_services(["FrontEnd/pnpm-lock.yaml"]),
            [
                "spring-member-web",
                "spring-community-web",
                "spring-stock-web",
                "spring-admin-web",
                "spring-admin-users-web",
                "spring-admin-logs-web",
            ],
        )

    def test_stock_service_image_builds_from_backend_context(self) -> None:
        stock_image = next(
            image
            for image in select_build_matrix.BACKEND_IMAGES
            if image["service"] == "spring-member-stock-service"
        )

        self.assertEqual(stock_image["context"], "BackEnd")

    def test_database_migrations_target_selects_exactly_three_flyway_images(self) -> None:
        selected, update_targets = select_build_matrix.select_services(
            "workflow_dispatch",
            "database-migrations",
            [],
        )

        self.assertEqual(selected, select_build_matrix.DATABASE_MIGRATION_IMAGES)
        self.assertEqual(update_targets, select_build_matrix.DATABASE_MIGRATION_IMAGES)

    def test_all_backend_target_excludes_frontend_images(self) -> None:
        selected, update_targets = select_build_matrix.select_services(
            "workflow_dispatch",
            "all-backend",
            [],
        )

        self.assertEqual(selected, select_build_matrix.ALL_BACKEND_IMAGES)
        self.assertEqual(update_targets, select_build_matrix.ALL_BACKEND_IMAGES)


if __name__ == "__main__":
    unittest.main()
