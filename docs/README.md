# Architecture diagrams

## Component diagram

- **Source:** [component_diagram.mmd](component_diagram.mmd) (Mermaid)
- **PNG (included):** [grpc_architecture_component_diagram.png](../grpc_architecture_component_diagram.png) in repo root — used in [README.md](../README.md) and [ARCHITECTURE.md](../ARCHITECTURE.md)
- **Rendered in:** README and ARCHITECTURE (Mermaid block; GitHub/GitLab etc. render it)

### Regenerate or export to SVG

From the project root:

```bash
# Install Mermaid CLI once (optional)
npm install -g @mermaid-js/mermaid-cli

# Export PNG
npx @mermaid-js/mermaid-cli -i docs/component_diagram.mmd -o docs/grpc_architecture_component_diagram.png

# Export SVG
npx @mermaid-js/mermaid-cli -i docs/component_diagram.mmd -o docs/grpc_architecture_component_diagram.svg
```

Then in ARCHITECTURE.md you can reference:

- `![GRPC component diagram](docs/grpc_architecture_component_diagram.png)`
- Download links to the PNG/SVG in `docs/`.
