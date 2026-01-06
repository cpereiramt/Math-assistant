package com.claySoftware.MathExpAssistant.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AwsSecretsEnvironmentPostProcessor implements EnvironmentPostProcessor {
    private static final Logger log = LoggerFactory.getLogger(AwsSecretsEnvironmentPostProcessor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean explicit = Boolean.parseBoolean(environment.getProperty("USE_AWS_SECRETS", "false"));
        boolean prodProfile = environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"));
        boolean prodFlag = Boolean.parseBoolean(environment.getProperty("app.secrets.use-aws", "false"));

        boolean shouldUseAws = explicit || prodProfile || prodFlag || looksLikeEc2();
        if (!shouldUseAws) {
            log.debug("AWS secrets loader: not enabled (explicit={}, prodProfile={}, prodFlag={}, ec2={})",
                    explicit, prodProfile, prodFlag, false);
            return;
        }

        String namesCsv = environment.getProperty("aws.secrets.names", environment.getProperty("AWS_SECRETS_NAMES"));
        if (namesCsv == null || namesCsv.trim().isEmpty()) {
            throw new IllegalStateException(
                    "AWS secrets enabled but no secret names configured (aws.secrets.names or AWS_SECRETS_NAMES)");
        }

        List<String> names = List.of(namesCsv.split("\\s*,\\s*"));
        Map<String, Object> props = new HashMap<>();

        try (SecretsManagerClient client = SecretsManagerClient.builder()
                // region can be provided via env/instance profile; do not hardcode
                .build()) {
            for (String name : names) {
                try {
                    GetSecretValueRequest req = GetSecretValueRequest.builder().secretId(name).build();
                    GetSecretValueResponse resp = client.getSecretValue(req);
                    String secretString = resp.secretString();
                    if (secretString == null)
                        continue;
                    if (secretString.trim().startsWith("{")) {
                        Map<String, Object> map = MAPPER.readValue(secretString,
                                new TypeReference<Map<String, Object>>() {
                                });
                        // flatten simple values to strings
                        for (Map.Entry<String, Object> e : map.entrySet()) {
                            props.put(e.getKey(), e.getValue() == null ? "" : e.getValue().toString());
                        }
                    } else {
                        // store raw secret under the secret name key
                        props.put(name, secretString);
                    }
                    log.info("Loaded secret '{}' from AWS Secrets Manager", name);
                } catch (Exception ex) {
                    log.error("Failed to load secret '{}': {}", name, ex.getMessage());
                    throw new IllegalStateException("Failed to load AWS secret: " + name, ex);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create AWS SecretsManager client", e);
        }

        environment.getPropertySources().addFirst(new MapPropertySource("aws-secrets", props));
        log.info("AWS secrets injected into Environment ({}) keys", props.keySet().size());
    }

    private boolean looksLikeEc2() {
        try {
            // IMDSv2 token request
            URL tokenUrl = new URL("http://169.254.169.254/latest/api/token");
            HttpURLConnection conn = (HttpURLConnection) tokenUrl.openConnection();
            conn.setRequestMethod("PUT");
            conn.setConnectTimeout(300);
            conn.setReadTimeout(300);
            conn.setRequestProperty("X-aws-ec2-metadata-token-ttl-seconds", "60");
            conn.setDoOutput(true);
            int code = conn.getResponseCode();
            if (code != 200)
                return false;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String token = r.lines().collect(Collectors.joining());
                if (token.isEmpty())
                    return false;
                // try a metadata call
                URL md = new URL("http://169.254.169.254/latest/meta-data/placement/availability-zone");
                HttpURLConnection mdConn = (HttpURLConnection) md.openConnection();
                mdConn.setRequestProperty("X-aws-ec2-metadata-token", token);
                mdConn.setConnectTimeout(300);
                mdConn.setReadTimeout(300);
                int mdCode = mdConn.getResponseCode();
                return mdCode == 200;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
