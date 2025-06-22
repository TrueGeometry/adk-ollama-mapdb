package com.truegeometry.adk;

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
 

import com.google.adk.agents.BaseAgent;
import com.google.adk.artifacts.InMemoryArtifactService;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.InMemorySessionService;

/**
 *
 * @author manoj.kumar
 */
 


import com.google.adk.agents.BaseAgent;
import com.google.adk.artifacts.InMemoryArtifactService;
import com.google.adk.sessions.InMemorySessionService;
import java.io.IOException;

/** The class for the in-memory GenAi runner, using in-memory artifact and session services. */
public class MapDbRunner extends Runner {

  public MapDbRunner(BaseAgent agent) throws IOException {
    // TODO: Change the default appName to InMemoryRunner to align with adk python.
    // Check the dev UI in case we break something there.
    this(agent, /* appName= */ agent.name());
  }

  public MapDbRunner(BaseAgent agent, String appName) throws IOException {
    super(agent, appName, new InMemoryArtifactService(), new MapDbSessionService(appName));
  }
}