# bridge-api — Makefile
# SDD v2.3 产品版本治理：make release 发版

.DEFAULT_GOAL := help

.PHONY: help release

help: ## 显示可用目标
	@grep -E '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

.PHONY: release
release: ## 发版（SDD v2.3）：bump 版本 + CHANGELOG + tag + GitHub Release。用法: make release patch|minor|major
	@bash ~/projects/_sdd-ops/scripts/make-release.sh $(filter-out $@,$(MAKECMDGOALS))
