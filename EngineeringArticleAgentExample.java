package com.truegeometry.adk;

/**
 *
 * @author ryzen
 */
/**
 * Example Agent workflow for generating engineering articles on a given topic
 * within a specified engineering discipline, including formulas and calculations,
 * using the Google ADK.
 */


import com.truegeometry.adk.OllamaBaseLM;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LoopAgent;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
 
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.truegeometry.adk.MapDbRunner;
import com.truegeometry.adk.MapDbRunner;
import io.reactivex.rxjava3.core.Flowable;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

// Changed class name
public class EngineeringArticleAgentExample extends BaseAgent {

  // --- Constants ---
  private static final String APP_NAME = "engineering_article_app"; // Changed app name
  private static final String USER_ID = "engineering_user_123"; // Changed user ID
  private static final String SESSION_ID = "article_session_45678"; // Changed session ID
  private static final String MODEL_NAME = "gemini-2.0-flash"; // Ensure this model is available

  private static final Logger logger = Logger.getLogger(EngineeringArticleAgentExample.class.getName()); // Changed logger name

  // Key for storing the engineering discipline in session state
  private static final String DISCIPLINE_SESSION_KEY = "engineering_discipline";
  // Key for storing the article topic in session state
  private static final String TOPIC_SESSION_KEY = "topic";
  // Key for storing the current article draft in session state
  private static final String ARTICLE_SESSION_KEY = "current_article";
  // Key for storing the technical critique in session state
  private static final String CRITIQUE_SESSION_KEY = "technical_critique";
    // Key for storing final suggestions in session state
  private static final String SUGGESTIONS_SESSION_KEY = "final_suggestions";


  private final String engineeringDiscipline; // Field to hold the discipline

  // Agents specific to article generation and refinement
  private final LlmAgent articleGenerator;
  private final LlmAgent technicalDetailer; // New agent for formulas/calculations
  private final LoopAgent technicalReviewAndRevisionLoop; // Adapted loop
  private final SequentialAgent finalChecks; // Adapted sequential

  public EngineeringArticleAgentExample(
      String name,
      String engineeringDiscipline, // Add discipline parameter
      LlmAgent articleGenerator,
      LlmAgent technicalDetailer,
      LoopAgent technicalReviewAndRevisionLoop,
      SequentialAgent finalChecks) {
    super(
        name,
        "Orchestrates engineering article generation, technical detailing, review, revision, and final checks for " + engineeringDiscipline + ".", // Update description
        List.of(articleGenerator, technicalDetailer, technicalReviewAndRevisionLoop, finalChecks),
        null,
        null);

    this.engineeringDiscipline = engineeringDiscipline; // Store the discipline
    this.articleGenerator = articleGenerator;
    this.technicalDetailer = technicalDetailer;
    this.technicalReviewAndRevisionLoop = technicalReviewAndRevisionLoop;
    this.finalChecks = finalChecks;
  }

  public static void main(String[] args) {

    // --- Define the individual LLM agents for engineering article generation ---

    // Agent 1: Generates the initial draft article
    LlmAgent articleGenerator =
        LlmAgent.builder()
            .name("ArticleGenerator")
            .model(new OllamaBaseLM("gemma3:12b"))
            .description("Generates the initial draft of an engineering article for a given discipline and topic.")
            .instruction(
                "  You are a technical writer specializing in the field provided in session state with key '%s'.\n" +
"              Write a draft engineering article (around 300-500 words) about the topic\n" +
"              provided in session state with key '%s'. The article should introduce the concept\n" +
"              and its significance within the field of %s. Do NOT include specific formulas or detailed calculations yet.".formatted(DISCIPLINE_SESSION_KEY, TOPIC_SESSION_KEY, "${session.state.%s}".formatted(DISCIPLINE_SESSION_KEY)) // Use session state for discipline
            )
            .inputSchema(null)
            .outputKey(ARTICLE_SESSION_KEY) // Key for storing article draft in session state
            .build();

     // Agent 2: Adds technical details (formulas and calculations)
     // This agent requires careful prompting to ensure it generates valid technical content.
     // It might be beneficial to provide context or examples in a real application.
     LlmAgent technicalDetailer =
         LlmAgent.builder()
             .name("TechnicalDetailer")
             .model(new OllamaBaseLM("gemma3:12b"))
             .description("Adds relevant formulas and example calculations to the engineering article.")
             .instruction(
                 " You are an expert in the field provided in session state with key '%s'. Review the engineering article draft\n" +
"               provided in session state with key '%s'. Add relevant formulas and one or two simple example calculations\n" +
"               to illustrate the concepts discussed. Use common engineering notation and clearly label formulas.\n" +
"               Ensure the additions flow well within the existing text. Output the complete, updated article.\n" +
"               For formulas, use LaTeX or a clear inline format like: F = m * a (Newton's Second Law).\n" +
"               For calculations, show the input values, the formula used, and the result clearly.".formatted(DISCIPLINE_SESSION_KEY, ARTICLE_SESSION_KEY) // Use session state for discipline
             )
             .inputSchema(null)
             .outputKey(ARTICLE_SESSION_KEY) // Overwrites the article with added details
             .build();


    // Agent 3: Critiques the technical content
    LlmAgent technicalReviewer =
        LlmAgent.builder()
            .name("TechnicalReviewer")
            .model(new OllamaBaseLM("gemma3:12b"))
            .description("Critiques the technical accuracy and clarity of the article, including formulas and calculations.")
            .instruction(
                " You are a peer reviewer for a journal in the field provided in session state with key '%s'. Review the article provided in\n" +
"              session state with key '%s'. Provide 1-3 clear points of constructive criticism on:\n" +
"              1. Technical accuracy of concepts, formulas, or calculations relevant to %s.\n" +
"              2. Clarity of explanations.\n" +
"              3. Relevance and correctness of included formulas/calculations.\n" +
"              If no technical issues are found, output 'Technical review complete. No major issues found.'\n" +
"              ".formatted(DISCIPLINE_SESSION_KEY, ARTICLE_SESSION_KEY, "${session.state.%s}".formatted(DISCIPLINE_SESSION_KEY)) // Use session state for discipline
            )
            .inputSchema(null)
            .outputKey(CRITIQUE_SESSION_KEY) // Key for storing critique
            .build();

    // Agent 4: Revises the article based on critique
    LlmAgent articleReviser =
        LlmAgent.builder()
            .name("ArticleReviser")
            .model(new OllamaBaseLM("gemma3:12b"))
            .description("Revises the engineering article based on technical critique.")
            .instruction(
                " You are a technical writer revising an article. Revise the engineering article provided in\n" +
"              session state with key '%s', based on the technical critique in\n" +
"              session state with key '%s'. Address the points raised to improve accuracy, clarity, and correctness.\n" +
"              Output only the revised article.\n" +
"              ".formatted(ARTICLE_SESSION_KEY, CRITIQUE_SESSION_KEY)
            )
            .inputSchema(null)
            .outputKey(ARTICLE_SESSION_KEY) // Overwrites the article
            .build();

    // Agent 5: Checks grammar and potentially formatting
    LlmAgent formatAndGrammarCheck =
        LlmAgent.builder()
            .name("FormatAndGrammarCheck")
            .model(new OllamaBaseLM("gemma3:12b"))
            .description("Checks grammar, spelling, and basic technical formatting of the article.")
            .instruction(
                " You are a proofreader for an engineering publication. Check the grammar, spelling, and basic formatting\n" +
"               of the engineering article provided in session state with key '%s'.\n" +
"               Output only the suggested corrections as a list, or output 'Article formatting and grammar are good!' if there are no errors.\n" +
"              ".formatted(ARTICLE_SESSION_KEY)
            )
            .outputKey(SUGGESTIONS_SESSION_KEY) // Key for storing suggestions
            .build();

    // Adapted Loop Agent: Technical review and revision
    LoopAgent technicalReviewAndRevisionLoop =
        LoopAgent.builder()
            .name("TechnicalReviewAndRevisionLoop")
            .description("Iteratively critiques and revises the engineering article.")
            .subAgents(technicalReviewer, articleReviser)
            .maxIterations(2) // Limit iterations to avoid infinite loops
            .build();

    // Adapted Sequential Agent: Final Checks
    SequentialAgent finalChecks =
        SequentialAgent.builder()
            .name("FinalChecks")
            .description("Performs final format and grammar checks.")
            .subAgents(formatAndGrammarCheck) // Only grammar/format check for simplicity
            .build();


    // --- Instantiate the main orchestration agent for a specific discipline ---
    String desiredDiscipline = "Mechanical Engineering"; // Example discipline
    EngineeringArticleAgentExample engineeringArticleAgentExample =
        new EngineeringArticleAgentExample(
            APP_NAME,
            desiredDiscipline, // Pass the discipline here
            articleGenerator,
            technicalDetailer,
            technicalReviewAndRevisionLoop,
            finalChecks);

    // --- Run the Agent with different topics ---
     System.out.println("--- Running Agent for " + desiredDiscipline + " ---");
    // Example topics for Mechanical Engineering
    // runAgent(engineeringArticleAgentExample, "Thermodynamics: First Law");
     //runAgent(engineeringArticleAgentExample, "Stress and Strain Analysis in Beams");
     runAgent(engineeringArticleAgentExample, "Fluid Dynamics: Bernoulli's Principle");

    // --- Example of running the agent for a different discipline ---
//    String anotherDiscipline = "Electrical Engineering"; // Example discipline
//     System.out.println("\n--- Running Agent for " + anotherDiscipline + " ---");
//    EngineeringArticleAgentExample electricalArticleAgentExample =
//        new EngineeringArticleAgentExample(
//            APP_NAME + "_electrical", // Use a different name or handle session IDs carefully
//            anotherDiscipline, // Pass the new discipline
//            articleGenerator, // Reuse agents (they use session state)
//            technicalDetailer,
//            technicalReviewAndRevisionLoop,
//            finalChecks);
//
//    // Example topic for Electrical Engineering
//     runAgent(electricalArticleAgentExample, "Ohm's Law and Circuit Analysis");

  }

  // --- Function to Interact with the Agent ---
  // Modified to accept the agent instance which already knows its discipline
  public static void runAgent(EngineeringArticleAgentExample agent, String userTopic) {
    // --- Setup Runner and Session ---
    // Note: For multiple runs in main(), you might need different runner/session IDs
    // or careful state management if reusing the same runner/session setup.
    // Here, we create a new runner/session for each run call for simplicity,
    // using a potentially modified SESSION_ID.
    MapDbRunner runner = null;
      try {
          runner = new MapDbRunner(agent);
      } catch (IOException ex) {
          Logger.getLogger(EngineeringArticleAgentExample.class.getName()).log(Level.SEVERE, null, ex);
      }
     // Create a unique session ID for each run call
    String uniqueSessionId = SESSION_ID + "_" + System.currentTimeMillis();


    Map<String, Object> initialState = new HashMap<>();
    initialState.put(DISCIPLINE_SESSION_KEY, agent.engineeringDiscipline); // Set the discipline in state
    initialState.put(TOPIC_SESSION_KEY, userTopic); // Set the user's topic in state
    initialState.put(ARTICLE_SESSION_KEY, ""); // Initialize article state
    initialState.put(CRITIQUE_SESSION_KEY, ""); // Initialize critique state
     initialState.put(SUGGESTIONS_SESSION_KEY, ""); // Initialize suggestions state


    Session session =
        runner
            .sessionService()
            .createSession(APP_NAME, USER_ID, new ConcurrentHashMap<>(initialState), uniqueSessionId)
            .blockingGet();
    logger.log(Level.INFO, () -> String.format("Initial session state for session %s: %s", uniqueSessionId, session.state()));

    // The topic and discipline are already set in the initial state,
    // but we can log the effective topic for clarity.
    logger.log(Level.INFO, () -> String.format("Running article generation for topic '%s' in discipline '%s' (Session %s).",
        userTopic, agent.engineeringDiscipline, uniqueSessionId));


    Content userMessage = Content.fromParts(Part.fromText("Generate an article about: " + userTopic));
    // Use the modified session object for the run
    Flowable<Event> eventStream = runner.runAsync(USER_ID, session.id(), userMessage);

    final String[] finalResponse = {"No final response captured."};
     // Using a synchronized list or similar might be better in a multithreaded scenario,
     // but for this example, a simple array wrapper is sufficient to capture the last value.
     final String[] finalArticleContent = {""};


    eventStream.blockingForEach(
        event -> {
          // Capture the final article content when the main agent completes
           if (event.author() != null && event.author().equals(agent.name())) { // Check if event is from the main agent
             Optional<String> textOpt =
                event
                    .content()
                    .flatMap(Content::parts)
                    .filter(parts -> !parts.isEmpty())
                    .map(parts -> parts.get(0).text().orElse(""));

             if (event.finalResponse() && textOpt.isPresent()) {
                 finalResponse[0] = textOpt.get(); // Capture the explicit final response
             }

            // Log other events for visibility into the process
               logger.log(Level.INFO, () ->
                String.format("Event from [%s] (Session %s): %s", event.author(), session.id(), event.toJson()));
          } else {
              // Log events from sub-agents
              logger.log(Level.INFO, () ->
                String.format("Event from sub-agent [%s] (Session %s): %s", event.author(), session.id(), event.toJson()));
          }
        });

    System.out.println("\n--- Agent Interaction Result (Session " + session.id() + ") ---");

     // Retrieve session again to get the final article state
    Session finalSession =
        runner
            .sessionService()
            .getSession(APP_NAME, USER_ID, session.id(), Optional.empty())
            .blockingGet();

    assert finalSession != null;
    // System.out.println("Final Session State:" + finalSession.state()); // Optional: print full state

    // Print the final article from the session state
    System.out.println("\n--- Final Generated Article for '" + userTopic + "' (" + agent.engineeringDiscipline + ") ---");
    Object finalArticleObj = finalSession.state().get(ARTICLE_SESSION_KEY);
    if (finalArticleObj != null && !String.valueOf(finalArticleObj).isEmpty()) {
        System.out.println(finalArticleObj);
    } else {
        System.out.println("Article generation failed or resulted in empty content for topic '" + userTopic + "'.");
         // Fallback to printing the final response event if available
        if (!finalResponse[0].equals("No final response captured.")) {
             System.out.println("\n--- Final Response Event Content (if available) ---");
             System.out.println(finalResponse[0]);
        }
    }

    System.out.println("-------------------------------\n");
  }

  // Helper method to check if the initial article is generated (adapted from story example)
  private boolean isArticleGenerated(InvocationContext ctx) {
    Object currentArticleObj = ctx.session().state().get(ARTICLE_SESSION_KEY);
    return currentArticleObj != null && !String.valueOf(currentArticleObj).isEmpty();
  }

  @Override
  protected Flowable<Event> runAsyncImpl(InvocationContext invocationContext) {
    // Implements the custom orchestration logic for the article workflow.
    String currentDiscipline = (String) invocationContext.session().state().get(DISCIPLINE_SESSION_KEY);
    String currentTopic = (String) invocationContext.session().state().get(TOPIC_SESSION_KEY);
    logger.log(Level.INFO, () -> String.format("[%s] Starting engineering article generation workflow for '%s' in '%s'.", name(), currentTopic, currentDiscipline));

    // Stage 1. Initial Article Generation
    // No input content is strictly needed as prompts read from session state
    Flowable<Event> articleGenFlow = runStage(articleGenerator, invocationContext, null, "ArticleGenerator");

    // Stage 2: Add Technical Details (Formulas and Calculations)
    // This runs after the initial draft is generated.
    Flowable<Event> technicalDetailingFlow = Flowable.defer(() -> {
        if (!isArticleGenerated(invocationContext)) {
            logger.log(Level.SEVERE, () ->
                String.format("[%s] Failed to generate initial article. Aborting before TechnicalDetailer.", name()));
            return Flowable.empty(); // Stop if no initial article
        }
        logger.log(Level.INFO, () ->
            String.format("[%s] Article state after generator: %s...", name(), truncateString(String.valueOf(invocationContext.session().state().get(ARTICLE_SESSION_KEY)), 200)));

        // Clear previous technical critique before starting detailing - maybe not needed here,
        // as critique happens *after* detailing, but good practice to manage state.
        // invocationContext.session().state().remove(CRITIQUE_SESSION_KEY);

         // No specific input content needed, agent reads article from state
        return runStage(technicalDetailer, invocationContext, null, "TechnicalDetailer");
    });


    // Stage 3: Technical Review and Revision Loop (runs after detailing completes)
    Flowable<Event> reviewAndRevisionFlow = Flowable.defer(() -> {
        if (!isArticleGenerated(invocationContext)) {
             logger.log(Level.SEVERE, () ->
                 String.format("[%s] No article available after TechnicalDetailer. Aborting before Review Loop.", name()));
            return Flowable.empty(); // Stop if no article after detailing
        }
         logger.log(Level.INFO, () ->
            String.format("[%s] Article state after detailer: %s...", name(), truncateString(String.valueOf(invocationContext.session().state().get(ARTICLE_SESSION_KEY)), 200)));
         // No specific input content needed
        return runStage(technicalReviewAndRevisionLoop, invocationContext, null, "TechnicalReviewAndRevisionLoop");
    });

    // Stage 4: Final Checks (runs after the review/revision loop completes)
    Flowable<Event> finalChecksFlow = Flowable.defer(() -> {
         if (!isArticleGenerated(invocationContext)) {
             logger.log(Level.SEVERE, () ->
                 String.format("[%s] No article available after Review Loop. Aborting before Final Checks.", name()));
            return Flowable.empty(); // Stop if no article after loop
        }
        logger.log(Level.INFO, () ->
            String.format("[%s] Article state after loop: %s...", name(), truncateString(String.valueOf(invocationContext.session().state().get(ARTICLE_SESSION_KEY)), 200)));
         // No specific input content needed
        return runStage(finalChecks, invocationContext, null, "FinalChecks");
    });

     // Stage 5: Output Final Article from Session State
     // This isn't a separate agent run, but a way to ensure the final content is part of the main agent's output events.
     Flowable<Event> finalOutputFlow = Flowable.defer(() -> {
         String finalArticleContent = (String) invocationContext.session().state().get(ARTICLE_SESSION_KEY);
          if (finalArticleContent != null && !finalArticleContent.isEmpty()) {
              logger.log(Level.INFO, () -> String.format("[%s] Emitting final article content.", name()));
              // Create a final response event from the main agent
              return Flowable.just(Event.builder()
                  .author(name()) // Author the event as the main agent
                  .content(Content.fromParts(Part.fromText(finalArticleContent)))
                 
                  .build());
          } else {
               logger.log(Level.WARNING, () -> String.format("[%s] Final article content is empty. Cannot emit final response.", name()));
               return Flowable.empty();
          }
     });


    // Combine the flows sequentially
    return Flowable.concatArray(
        articleGenFlow,
        technicalDetailingFlow,
        reviewAndRevisionFlow,
        finalChecksFlow,
        finalOutputFlow // Add the final output stage
    ).doOnComplete(() -> logger.log(Level.INFO, () -> String.format("[%s] Workflow finished.", name())));
  }

  // Helper method for a single agent run stage with logging and optional input content
  private Flowable<Event> runStage(BaseAgent agentToRun, InvocationContext ctx, Content inputContent, String stageName) {
    logger.log(Level.INFO, () -> String.format("[%s] Running %s...", name(), stageName));
    
    return agentToRun
        // Use the overloaded runAsync that accepts explicit input content if needed
        // For this workflow, agents read from session state, so inputContent can be null
        .runAsync(ctx)
        .doOnNext(event -> {
            // Log only the type and author for brevity, full event JSON can be noisy
            logger.log(Level.INFO,() ->
                String.format("[%s] Event from %s [%s]", name(), stageName, event.author() != null ? event.author() : "UNKNOWN"));
        })
        .doOnError(err ->
            logger.log(Level.SEVERE,
                String.format("[%s] Error in %s", name(), stageName), err))
        .doOnComplete(() ->
            logger.log(Level.INFO, () ->
                String.format("[%s] %s finished.", name(), stageName)));
  }

  // Helper to truncate strings for logging
  private static String truncateString(String s, int maxLength) {
      if (s == null) return "null";
      if (s.length() <= maxLength) return s;
      return s.substring(0, maxLength - 3) + "...";
  }


  @Override
  protected Flowable<Event> runLiveImpl(InvocationContext invocationContext) {
    // Live implementation is not necessary for this example
    return Flowable.error(new UnsupportedOperationException("runLive not implemented for this example."));
  }
}