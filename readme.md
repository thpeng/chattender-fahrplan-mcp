# 🚆 Der chattende Fahrplan – a Swiss Timetable MCP Server

A **Spring AI / MCP-compatible server** that provides **SBB timetable information** to language models.  
Tested with **OpenAI OSS GPT (20B)**, **LM Studio (Apertus 8B)**, and **Qwen-3 8B** via LM Studio.  
Integrated in an online-solution based on **Mistral Large 123b with Le Chat** and a accessible MCP server on GCP.
Enables *bring-your-own LLM* scenarios to interact with the SBB timetable.  
This is an **experimental study project** with **no warranties** regarding accuracy, completeness, or runtime behavior.

Inspired by the 1996 “Sprechender Fahrplan” by SBB

---

## Overview

The repository extends the MCP-compatible Spring Boot server with five LLM tools for Swiss railway journey planning.

These tools allow large language models to query the SBB Journey API directly, selecting the correct function depending on user intent and time context.

### Tools

- **nextJourney** – returns a natural-language description of the next available connection between two stations (used for “now”, “next train”, “soon”).
- **planJourney** – returns the next available connection after a specific ISO datetime (used for “today at 14:35”, “tomorrow 07:10”).
- **listJourneys** – returns several upcoming connections for today as JSON (used for “show me the options”).
- **listAndPlanJourneys** – returns several connections starting from a given datetime as JSON (used for “show me trains after 16:00”).
- **raw** – returns the unprocessed JSON response from the SBB Journey Service (for debugging or analysis).  
  *Note: this method often overwhelms most language models due to the large JSON payload size.*

An additional helper tool **datum** provides today’s date in ISO format for time resolution.

This demonstrates how LLMs can perform real-time timetable queries, combine multiple result formats, and reason over structured SBB journey data within the Model Context Protocol (MCP) framework.

---

## Components in the Repository

- **infrastructure** – infrastructure and security components
- **journeyservice** – integration with the SBB journey service
- **mapping** – mapping from journey-service data to LLM-digestible datatypes
- **TimetableTool** – exposes the LLM tools
- **Minimal Jinja Template** – used for LM Studio integration (tested with Apertus models)
- **Example MCP Snippet** – shows how to connect the local server with LM Studio

---

## Integration with LLMs

The MCP server can be connected to various LLM runtimes and frontends.  
Below is an overview of tested integrations:

| Interface | LLM Vendor    | LLM Model          | MCP Runtime | Text | Voice In | Voice Out |
|-----------|---------------|--------------------|--------------|------|---------|----------|
| LM Studio | Google        | Gemma-3 270m       | local        | ❌    | ❌ | ❌ |
| LM Studio | Google        | Gemma-3 1b         | local        | ❌    | ❌ | ❌ |
| LM Studio | Google        | Gemma-3 4b         | local        | ✅    | ❌ | ❌ |
| LM Studio | Swiss AI      | Apertus 8B         | local        | ❌    | ❌ | ❌ |
| LM Studio | Alibaba Cloud | Qwen3 8B           | local        | ✅    | ❌ | ❌ |
| LM Studio | OpenAI        | OSS GPT 20B        | local        | ✅    | ❌ | ❌ |
| Le Chat   | Mistral AI    | Mistral Large 123B | GCP          | ✅    | ✅ | ❌ |
| Claude    | Anthropic     | Claude Sonnet 4.5  | GCP          | ✅    | ✅ | ✅ |
| ChatGPT   | OpenAI        | GPT-5              | GCP          | ✅    | ✅ | ❌ |

Legend:  
✅ = verified working  
❌ = not supported  / did not work
❓ = not tested 


---

## Runtime

The MCP server runs on:

- **Local** – requires Java 21; define
    - `JOURNEY_SERVICE_BASE`
    - `MCP_API_KEY`
    - `JOURNEY_SERVICE_CLIENT_ID`
    - `JOURNEY_SERVICE_CLIENT_SECRET`

- **GCP** – deployable on Cloud Run (see `cloudbuild.yaml` and `project.toml`); configured for Zurich.  
  Define the same environment variables as above.  
  Cloud deployment is required to interface with publicly available models (e.g., Mistral).

---

## Limitations

- **Apertus models** may fail to parse or render complex JSON structures correctly due to missing tool support.
- **Journey-service integration** currently does not handle all edge cases. Lacks situations, doesn't communicate clearly if a train is delayed. 
- **Gemini** is not supported because the MCP capability is only available with the cli / sdk. 
- **Claude** and **ChatGTP** follow strictly the MCP spec and support each only OAuth2 or no authentication. To limit access, the mcp server requires a header with a secret. 
---

## Acknowledgments

- **Swiss AI** – for the Apertus model (somewhat disappointing due to missing MCP/tool support and the claim that “Totemügerli” is a peak in Valais ;) )
- **DSO KIS** – for providing journey-service access
- **@blancsw** – for the Jinja template basis (see [Hugging Face discussion](https://huggingface.co/swiss-ai/Apertus-8B-Instruct-2509/discussions/18))
- **ChatGPT** – for the vibe coding sessions 

---

## Contact

For questions, please open an issue via **GitHub Issues**.
