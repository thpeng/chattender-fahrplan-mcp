# 🚆 Der chattende Fahrplan - a Swiss Timetable MCP Server

A **Spring AI / MCP-compatible server** that provides **SBB timetable information** to language models.  
Tested with **OpenAI OSS GPT (20B)**,  **LM Studio (Apertus 8B)** and Qwen-3 8b via LM Studio.
Should enable bring-you-own LLM to interact with the SBB timetable.
This is an experimental study project with no warranties regarding accuracy, completeness or runtime behavior. 

In memory of the 1996 Sprechender Fahrplan

---

## Overview

The repository contains a **MCP server** built with **Spring Boot WebFlux** and **Spring AI**.  
It offers two MCP tools for journey planning between Swiss railway stations:
- **`planJourneyJson`** – returns structured journey data as JSON (departure, arrival, service, operator, quay, direction)
- **`planJourneyText`** – returns a single natural-language sentence describing the next train connection

The project demonstrates how LLMs can interact with real-time data services using the **Model Context Protocol (MCP)**.

---

## Components in the Repository

- **infrastructure** – Infra- and security-components
- **journeyservice** – stuff to interact with the sbb journey-service
- **mapping** – from j-s data to llm-digestible datatypes
- **TimetableTool** – exposes the llm tools
- **Minimal Jinja Template** – located in the repo for LM Studio integration (used by Apertus models)
- **Example MCP Snippet** – showing how to connect the local server with LM Studio

---

## Integration with LLMs

The MCP server has been tested with:
- **OpenAI OSS GPT (20B)** – full JSON parsing supported. Seldom issues detected, mostly caused by too much information in the chat window. 
- **Apertus 8B** – limited parsing ability; works sometimes with the `planJourneyText` tool and the included **minimal Jinja template**
- **Qwen-3 8B** - very good results. Use this if possible with Lm Studio. 
- **Mistral / Le chat** but not with the android app. 

---

## Runtime

The MCP server runs on
- **Local** – Java 21 needed. define  **`JOURNEY_SERVICE_BASE`** ,  **`MCP_API_KEY`** ,  **`JOURNEY_SERVICE_CLIENT_ID`** ,  **`JOURNEY_SERVICE_CLIENT_SECRET`**
- **GCP** – works on GCP Cloud Run, see cloudbuild.yaml and project.toml. configured for zurich. define the same values as above

---


## Limitations

- **Apertus models** may fail to parse or render complex JSON correctly. Seems to be an issue because it lacks tool support
- **journey-service integration** misses several (edge) cases. For example, it does handle the stopover / connection change correctly.

---
## Acknowledgments

- **Swiss AI** for the apertus model. A bit of a let-down is the currently missing tool support and that according to apertus 'totemügerli' is a peak and mountain hut in valais. At least, apertus informed, a stay is only 35 swiss francs.
- **DSO KIS** for the journey-service access
- **@blancsw** for the basics of the jinja template - see https://huggingface.co/swiss-ai/Apertus-8B-Instruct-2509/discussions/18 
- **ChatGPT** for the vibe coding sessions

---

## Contact

For questions, please reach out via **GitHub Issues**.  

---
