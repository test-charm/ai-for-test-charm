package com.testcharm;

import io.cucumber.gherkin.Gherkin;
import io.cucumber.messages.IdGenerator;
import io.cucumber.messages.Messages;
import lombok.SneakyThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

public class KbProcessor {

    @SneakyThrows
    public void process(String srcDir, String dstDir) {
        Path srcPath = Paths.get(srcDir);
        Path dstPath = Paths.get(dstDir);
        Files.createDirectories(dstPath);

        try (Stream<Path> files = Files.walk(srcPath)) {
            files.filter(f -> f.toString().endsWith(".feature"))
                    .forEach(f -> processFeatureFile(f, srcPath, dstPath));
        }
    }

    @SneakyThrows
    private void processFeatureFile(Path file, Path srcRoot, Path dstRoot) {
        String formatted = formatFeature(Files.readString(file), file.toString());
        Path outputFile = dstRoot.resolve(srcRoot.relativize(file));
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, formatted);
    }

    private String formatFeature(String content, String uri) {
        IdGenerator idGenerator = new IdGenerator.Incrementing();
        List<Messages.Envelope> envelopes = Gherkin.fromSources(
                List.of(Messages.Envelope.newBuilder()
                        .setSource(Messages.Source.newBuilder()
                                .setUri(uri)
                                .setData(content)
                                .setMediaType("text/x.cucumber.gherkin+plain")
                                .build())
                        .build()),
                false, true, false, idGenerator
        ).toList();

        for (Messages.Envelope envelope : envelopes) {
            if (envelope.hasGherkinDocument()) {
                return prettyPrint(envelope.getGherkinDocument());
            }
        }
        return content;
    }

    private String prettyPrint(Messages.GherkinDocument doc) {
        StringBuilder sb = new StringBuilder();
        if (doc.hasFeature()) {
            printFeature(sb, doc.getFeature());
        }
        return sb.toString().stripTrailing();
    }

    private void printFeature(StringBuilder sb, Messages.GherkinDocument.Feature feature) {
        sb.append(feature.getKeyword()).append(": ").append(feature.getName()).append("\n");
        for (Messages.GherkinDocument.Feature.FeatureChild child : feature.getChildrenList()) {
            if (child.hasScenario()) {
                sb.append("\n");
                printScenario(sb, child.getScenario(), "  ");
            }
        }
    }

    private void printScenario(StringBuilder sb, Messages.GherkinDocument.Feature.Scenario scenario, String indent) {
        sb.append(indent).append(scenario.getKeyword()).append(": ").append(scenario.getName()).append("\n");
        for (Messages.GherkinDocument.Feature.Step step : scenario.getStepsList()) {
            printStep(sb, step, indent + "  ");
        }
    }

    private void printStep(StringBuilder sb, Messages.GherkinDocument.Feature.Step step, String indent) {
        sb.append(indent).append(step.getKeyword()).append(step.getText()).append("\n");
        if (step.hasDocString()) {
            printDocString(sb, step.getDocString(), indent + "  ");
        }
        if (step.hasDataTable()) {
            printDataTable(sb, step.getDataTable(), indent + "  ");
        }
    }

    private void printDocString(StringBuilder sb, Messages.GherkinDocument.Feature.Step.DocString docString, String indent) {
        String delimiter = docString.getDelimiter().isEmpty() ? "\"\"\"" : docString.getDelimiter();
        sb.append(indent).append(delimiter).append("\n");
        for (String line : docString.getContent().split("\n", -1)) {
            sb.append(indent).append(line.stripLeading()).append("\n");
        }
        sb.append(indent).append(delimiter).append("\n");
    }

    private void printDataTable(StringBuilder sb, Messages.GherkinDocument.Feature.Step.DataTable dataTable, String indent) {
        List<Messages.GherkinDocument.Feature.TableRow> rows = dataTable.getRowsList();
        int[] widths = new int[rows.get(0).getCellsCount()];
        for (Messages.GherkinDocument.Feature.TableRow row : rows) {
            for (int i = 0; i < row.getCellsCount(); i++) {
                widths[i] = Math.max(widths[i], row.getCells(i).getValue().length());
            }
        }

        for (Messages.GherkinDocument.Feature.TableRow row : rows) {
            sb.append(indent).append("|");
            for (int i = 0; i < row.getCellsCount(); i++) {
                String value = row.getCells(i).getValue();
                sb.append(" ").append(String.format("%-" + widths[i] + "s", value)).append(" |");
            }
            sb.append("\n");
        }
    }
}
