# 🚆 Der chattende Fahrplan - a Swiss Timetable MCP Server

A **Spring AI / MCP-compatible server** that provides **SBB timetable information** to language models.  
Tested with **OpenAI OSS GPT (20B)** and **LM Studio (Apertus 8B)**.
Should enable bring-you-own LLM to interact with the SBB timetable.
This is an experimental study project with no warranties regarding accuracy, completeness or runtime behavior. 

---

## Overview

The repository contains a **MCP server** built with **Spring Boot WebFlux** and **Spring AI**.  
It offers two MCP tools for journey planning between Swiss railway stations:
- **`planJourneyJson`** – returns structured journey data as JSON (departure, arrival, service, operator, quay, direction)
- **`planJourneyText`** – returns a single natural-language sentence describing the next train connection

The project demonstrates how LLMs can interact with real-time data services using the **Model Context Protocol (MCP)**.

---

## Components in the Repository

- **nfrastructure** – Infra- and security-components
- **journeyservice** – stuff to interact with the sbb journey-service
- **mapping** – from j-s data to llm-digestible datatypes
- **Two MCP Tools** – both accessible via `/mcp/tools` endpoints and registered automatically
- **TimetableTool** – exposes the llm tools
- **Minimal Jinja Template** – located in the repo for LM Studio integration (used by Apertus models)
- **Example MCP Snippet** – showing how to connect the local server with LM Studio

---

## Integration with LLMs

The MCP server has been tested with:
- **OpenAI OSS GPT (20B)** – full JSON parsing supported. Seldom issues detected, mostly caused by too much information in the chat window. 
- **Apertus 8B** – limited parsing ability; works sometimes with the `planJourneyText` tool and the included **minimal Jinja template**

The **Jinja template** must be used for proper tool invocation inside LM Studio.  
It enables Apertus sometimes to render tool calls and responses.

---


## Limitations

- **Apertus models** may fail to parse or render complex JSON correctly  
  → use `planJourneyText` for reliable output
- **OpenAI OSS GPT (20B)** handles both JSON and text modes well
- **journe-service integration** is not correctly implemented. via, legs and direction information are sometimes wrong

---

## Contact

For questions, please reach out via **GitHub Issues**.  

---
