# Evidencia de la etapa 0: preparación del entorno

Capturas tomadas el **16/11/2026**, durante la instalación en la notebook de Estefanía.

| # | Captura | Qué muestra |
|---|---|---|
| 1 | [01-claude-code-instalado.png](01-claude-code-instalado.png) | Claude Code instalado (versión 2.1.326): el agente de código que usamos. |
| 2 | [02-instalador-active-stack.png](02-instalador-active-stack.png) | El instalador de Active Stack (`active-stack_windows_amd64.exe install`) con su menú principal. |
| 3 | [03-tier-balanceado.png](03-tier-balanceado.png) | La elección del nivel de autonomía del agente: **balanceado**. |
| 4 | [04-skills-instaladas-y-openspec.png](04-skills-instaladas-y-openspec.png) | Los harnesses instalados en `~/.claude/skills`, todos del 16/11/2026 a las 19:33: active-orchestrator, discovery-research, kb-creator, roadmap-generator, find-skill, skill-registry, agent-instruction, design-system, sdd-orchestrator, web-scraper y skills de diseño. También muestra `openspec --version` (2.0.0). |

El registro de la instalación (`~/.active-stack/installation.json`) confirma el **modo Full** para Claude (`"mode": "full"`, `"tier": "balanceado"`).
