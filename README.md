# adk-ollama-mapdb
Support for Google's ADK with Ollama and mapdb for building agents

## Using the OllamaBaseLM

*new OllamaBaseLM("gemma3:12b")*


```
    // --- Define the individual LLM agents ---
    LlmAgent storyGenerator =
        LlmAgent.builder()
            .name("StoryGenerator")
            .model(new OllamaBaseLM("gemma3:12b"))
            .description("Generates the initial story.")
            .instruction(
                "  You are a story writer. Write a short story (around 100 words) about a cat,\n" +
"              based on the topic provided in session state with key 'topic'")
            .inputSchema(null)
            .outputKey("current_story") // Key for storing output in session state
            .build();
```

## Using MapDb Runner inplace of Inmemory Runner

*new MapDbRunner(agent)*

```
 MapDbRunner runner = null;
      try {
          runner = new MapDbRunner(agent);
      } catch (IOException ex) {
          Logger.getLogger(EngineeringArticleAgentExample.class.getName()).log(Level.SEVERE, null, ex);
      }
```

## Default Inmemory Runner

```
 // --- Setup Runner and Session ---
    InMemoryRunner runner = new InMemoryRunner(agent);
```
