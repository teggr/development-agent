package com.github.developmentagent.agents.copilot;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.copilot.CopilotClient;

@Configuration
public class CopilotConfiguration {

    @Bean(destroyMethod = "close")
    public CopilotClient copilotClient() {
        CopilotClient copilotClient = new CopilotClient();
        // need to configure this
        copilotClient.start();
        return copilotClient;
    }


        // try (var client = new CopilotClient()) {
        //     client.start().get();

        //     var workflow = new DeliveryWorkflow(client);
        //     client.stop().get();
        // }
}
