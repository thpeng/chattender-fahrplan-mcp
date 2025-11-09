# 🚆 Der chattende Fahrplan – a Swiss Timetable MCP Server

A **Spring AI / MCP-compatible server** that provides **SBB timetable information** to language models.  
Tested with **OpenAI OSS GPT (20B)**, **LM Studio (Apertus 8B)**, and **Qwen-3 8B** via LM Studio.  
Integrated in an online-solution based on **Mistral Large 123b with Le Chat** and a accessible MCP server on GCP.
Enables *bring-your-own LLM* scenarios to interact with the SBB timetable.  
This is an **experimental study project** with **no warranties** regarding accuracy, completeness, or runtime behavior.

Inspired by the 1996 “Sprechender Fahrplan” by SBB

---

## Overview

The repository contains an **MCP server** built with **Spring Boot WebFlux** and **Spring AI**.  
It provides two MCP tools for journey planning between Swiss railway stations:

- **`planJourneyJson`** – returns structured journey data as JSON (departure, arrival, service, operator, quay, direction)
- **`planJourneyText`** – returns a natural-language sentence describing the next train connection

The project demonstrates how LLMs can interact with real-time data services using the **Model Context Protocol (MCP)**.  
It also serves as a **testbed** to explore whether it is feasible to build a Swiss AI product with a Swiss AI stack.

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

The MCP server has been tested with:

- **OpenAI OSS GPT (20B)** – full JSON parsing supported; rare issues caused by excessive chat context
- **Apertus 8B** – limited parsing ability; works intermittently with `planJourneyText` and the included minimal Jinja template
- **Qwen-3 8B** – very good results; recommended when using LM Studio
- **Mistral Large 123B** – tested via *Le Chat*; very good results, including voice mode

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

---

## Acknowledgments

- **Swiss AI** – for the Apertus model (somewhat disappointing due to missing MCP/tool support and the claim that “Totemügerli” is a peak in Valais ;) )
- **DSO KIS** – for providing journey-service access
- **@blancsw** – for the Jinja template basis (see [Hugging Face discussion](https://huggingface.co/swiss-ai/Apertus-8B-Instruct-2509/discussions/18))
- **ChatGPT** – for the vibe coding sessions 

---

## Contact

For questions, please open an issue via **GitHub Issues**.
