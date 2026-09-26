# Release status: TracePilot 0.1.0

## Implemented and exercised

The Java engine and JDK HTTP application compile and run. The product's offline workflows, persistence contracts, background jobs, domain tests, real HTTP smoke, browser-module tests, and offline DOM rendering have been exercised. See TESTING.md and `validation/` for the recorded output.

## Implemented but awaiting target-environment verification

The Spring MVC adapter, Maven build, H2 tests, JDBC PostgreSQL path, Docker images/Compose deployment, and external provider integrations are source implementations, not claimed verified deployments. CI must run after publication. Live integrations require explicitly configured services and credentials.

## Deliberate implementation choices

- Java owns the actual domain algorithms. Python is only an optional CLI/testing client, not a claimed AI-worker fleet.
- Browser interfaces use native ES modules, not React. There is no frontend package installation required to use the applications.
- Local atomic-file persistence is the default. PostgreSQL is the optional JDBC deployment. No Hibernate/JPA, Flyway, Redis, or Kafka claim is made.
- Offline AI-related baselines are labelled as such. Real generation/semantic embeddings are separate opt-in Ollama paths.
- GraphRAG's Neo4j support is a Cypher export, not a live graph database integration.

## Bounds and exclusions

The checks are lexical heuristics, not ground-truth hallucination detection. Recorded/extractive timing measures local processing, not hosted-model performance. Token estimates are not model-tokenizer counts. The application evaluates bounded datasets; it is not a distributed trace collector or a replacement for a production observability system.

The repository is an initial, inspectable implementation with tests and documentation. No three-month development duration, production adoption, historical commit schedule, employer use, or measured commercial impact is asserted. Add a real changelog as you review, test, and extend the code.
