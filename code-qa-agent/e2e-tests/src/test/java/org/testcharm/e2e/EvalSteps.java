package org.testcharm.e2e;

import io.cucumber.java.zh_cn.当;
import io.cucumber.java.zh_cn.那么;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.testcharm.cucumber.restful.extensions.PathVariableReplacement;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.testcharm.dal.Assertions.expect;

@Slf4j
public class EvalSteps {

    @Value("${app.db.url}")
    private String dbUrl;

    @Value("${app.db.username}")
    private String dbUsername;

    @Value("${app.db.password}")
    private String dbPassword;

    @Value("${embedding.base-url:http://localhost:18002}")
    private String embeddingBaseUrl;

    private String lastAgentReply;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** Wait for the agent to finish, then query the database for the response text. */
    @SneakyThrows
    @当("收齐回复")
    public void 收齐回复() {
        // ThreadId is captured by 用户发送消息 step and stored in PathVariableReplacement
        String threadId = PathVariableReplacement.replacements.get("eval-thread-id");
        if (threadId == null || threadId.isBlank()) {
            throw new AssertionError("eval-thread-id not set. Ensure 用户发送消息 runs before 收齐回复.");
        }
        log.info("Tracking threadId={}", threadId);

        // Poll the DB until the agent produces a substantive response
        long deadline = System.currentTimeMillis() + 600_000;
        String lastContent = "";
        while (System.currentTimeMillis() < deadline) {
            String content = queryLastAssistantMessage(threadId);
            if (content != null && !content.isBlank() && !content.equals(lastContent)) {
                lastContent = content;
                long stableUntil = System.currentTimeMillis() + 5000;
                while (System.currentTimeMillis() < stableUntil) {
                    Thread.sleep(1000);
                    String check = queryLastAssistantMessage(threadId);
                    if (check == null || !check.equals(content)) {
                        lastContent = check != null ? check : "";
                        break;
                    }
                }
                if (System.currentTimeMillis() >= stableUntil) {
                    lastAgentReply = content;
                    break;
                }
            }
            Thread.sleep(2000);
        }

        if (lastAgentReply == null) {
            throw new AssertionError("Agent did not respond within 600s. Check agent logs.");
        }

        // Strip the timing footer appended by app.py ("\n\n---\n⏱️ 耗时 ...")
        int footerIdx = lastAgentReply.lastIndexOf("---");
        if (footerIdx > 0 && lastAgentReply.substring(footerIdx).contains("⏱️")) {
            lastAgentReply = lastAgentReply.substring(0, footerIdx).stripTrailing();
        }
        log.info("Captured reply via DB, length={}", lastAgentReply.length());
    }

    @SneakyThrows
    private String queryLastAssistantMessage(String threadId) {
        try (var conn = java.sql.DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
             var stmt = conn.createStatement()) {
            var rs = stmt.executeQuery(
                "SELECT output FROM steps " +
                "WHERE \"threadId\" = '" + threadId.replace("'", "''") + "' " +
                "AND type = 'assistant_message' " +
                "AND name = 'Assistant' " +
                "AND output IS NOT NULL " +
                "AND output != '👋 我已准备好分析代码库，请问你想了解什么？' " +
                "AND LENGTH(output) > 50 " +
                "ORDER BY \"createdAt\" DESC LIMIT 1"
            );
            if (rs.next()) {
                return rs.getString("output");
            }
        }
        return null;
    }

    /** Run DAL string assertions (::should.contains, ::should.matches, etc.) on the captured reply. */
    @那么("回复内容应满足:")
    public void 回复内容应满足(String dalExpression) {
        if (lastAgentReply == null) {
            throw new AssertionError("No reply captured. Call 收齐回复 before 回复内容应满足.");
        }
        expect(lastAgentReply).should(dalExpression);
    }

    /** Run NLI entailment: does golden text entail actual reply?
     *  score = entailment_prob - contradiction_prob, range [-1, 1].
     *  Positive = consistent, negative = contradictory.
     */
    @那么("回复蕴含度应大于 {double}:")
    public void 回复蕴含度应大于(double threshold, String goldenText) {
        if (lastAgentReply == null) {
            throw new AssertionError("No reply captured. Call 收齐回复 before 回复蕴含度.");
        }
        log.info("lastAgentReply {}", lastAgentReply);

        double score = callNliAggregated(lastAgentReply, goldenText.strip());

        log.info("NLI aggregated score={} threshold={}", score, threshold);

        if (score <= threshold) {
            throw new AssertionError(String.format(
                    "NLI entailment score %.4f <= %.2f threshold. Key factual claims are not entailed by the reply.", score, threshold));
        }
    }

    @SneakyThrows
    private double callNliAggregated(String actual, String golden) {
        // Split golden into individual factual claims (separated by blank lines or bullet-like markers)
        // and check each claim against the actual reply. Return the minimum entailment score.
        String[] claims = golden.split("\\n{2,}");  // split on blank lines
        if (claims.length <= 1) {
            // Fallback: split on sentence boundaries
            claims = golden.split("(?<=[。])\\s*");
        }
        if (claims.length == 1) {
            // Single claim — use as-is
            return callNliRaw(actual, golden.trim()).getDouble("score");
        }

        double totalScore = 0;
        int count = 0;
        for (String claim : claims) {
            String c = claim.strip();
            if (c.length() < 10) continue;
            double score = callNliRaw(c, actual).getDouble("score");
            log.info("NLI claim: '{}' ... → score={}", c.length() > 60 ? c.substring(0, 60) + "..." : c, score);
            totalScore += score;
            count++;
        }
        if (count == 0) return callNliRaw(golden.trim(), actual).getDouble("score");
        return totalScore / count;
    }

    @SneakyThrows
    private JSONObject callNliRaw(String hypothesis, String premise) {
        // hypothesis = the claim to check (from golden)
        // premise    = the actual agent reply
        JSONObject body = new JSONObject();
        body.put("text1", hypothesis);  // hypothesis (short claim)
        body.put("text2", premise);     // premise   (full agent reply)

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(embeddingBaseUrl + "/entailment"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new JSONObject(response.body());
    }
}
