EVENT?=jbcn
MODULES=fp-web fp-repo-service fp-commit-service

.PHONY: $(MODULES)

$(MODULES):
	docker build -f $@/src/main/docker/Dockerfile $@ -t fp -t integrationworkspace/$@:$(EVENT)-latest
	docker push integrationworkspace/$@:$(EVENT)-latest

prometheus-image:
	docker build -f prometheus/Dockerfile prometheus -t integrationworkspace/prometheus:$(EVENT)-latest

images: $(MODULES) prometheus-image


