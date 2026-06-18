package com.fadihania;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class App {
    private static final Path DEFAULT_INPUT = Path.of("models", "ecore-models.csv");
    private static final Path DEFAULT_OUTPUT = Path.of("out", "ecore-models.json");
    private static final String GITHUB_TOKEN_ENV = "GITHUB_TOKEN";

    private App() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (IllegalArgumentException | IllegalStateException | IOException e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args) throws IOException {
        CliOptions options = CliOptions.parse(args);
        String token = System.getenv(GITHUB_TOKEN_ENV);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Missing GITHUB_TOKEN environment variable.");
        }

        List<RepositoryInput> repositories = readRepositories(options.input());
        GitHub github = new GitHubBuilder().withOAuthToken(token).build();

        List<RepositoryStats> stats = new ArrayList<>();
        for (int i = 0; i < repositories.size(); i++) {
            RepositoryInput repositoryInput = repositories.get(i);
            System.out.printf("[%d/%d] Collecting %s%n", i + 1, repositories.size(), repositoryInput.repoName());
            stats.add(collectRepositoryStats(github, repositoryInput));
        }

        writeJson(options.output(), stats);
        System.out.printf("Wrote repository stats to %s%n", options.output());
    }

    private static List<RepositoryInput> readRepositories(Path input) throws IOException {
        Map<String, RepositoryInput> repositories = new LinkedHashMap<>();

        try (Reader reader = Files.newBufferedReader(input);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .build()
                     .parse(reader)) {
            for (CSVRecord record : parser) {
                String repoName = required(record, "repository_name");
                RepositoryInput repository = repositories.computeIfAbsent(
                        repoName,
                        key -> new RepositoryInput(key, required(record, "repository_url"), new ArrayList<>())
                );
                repository.modelFiles().add(new ModelFile(
                        required(record, "model_file_name"),
                        required(record, "full_file_url"),
                        parseLong(record, "file_size"),
                        parseLong(record, "commits")
                ));
            }
        }

        return List.copyOf(repositories.values());
    }

    private static RepositoryStats collectRepositoryStats(GitHub github, RepositoryInput repositoryInput) {
        RepositoryStats stats = RepositoryStats.empty(repositoryInput);

        try {
            GHRepository repository = github.getRepository(repositoryInput.repoName());
            stats.stars = repository.getStargazersCount();
            stats.forks = repository.getForksCount();
            stats.branches = repository.getBranches().size();
            stats.languages = repository.listLanguages();
            stats.contributors = repository.listContributors().toList().size();
            stats.issues = new IssueStats(repository.getOpenIssueCount(), countTotalIssues(repository));
            stats.pulls = countTotalPullRequests(repository);
            stats.createdAt = repository.getCreatedAt().toString();
            stats.updatedAt = repository.getUpdatedAt().toString();
            stats.size = repository.getSize();
        } catch (IOException | RuntimeException e) {
            stats.error = e.getMessage();
        }

        return stats;
    }

    private static int countTotalIssues(GHRepository repository) throws IOException {
        int total = 0;
        for (GHIssue issue : repository.queryIssues()
                .state(GHIssueState.ALL)
                .pageSize(100)
                .list()) {
            if (!issue.isPullRequest()) {
                total++;
            }
        }
        return total;
    }

    private static int countTotalPullRequests(GHRepository repository) {
        int total = 0;
        for (var ignored : repository.queryPullRequests()
                .state(GHIssueState.ALL)
                .pageSize(100)
                .list()) {
            total++;
        }
        return total;
    }

    private static void writeJson(Path output, List<RepositoryStats> stats) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        ObjectMapper mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(output.toFile(), stats);
    }

    private static String required(CSVRecord record, String fieldName) {
        String value = record.get(fieldName);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required CSV field '" + fieldName + "' at record " + record.getRecordNumber());
        }
        return value;
    }

    private static long parseLong(CSVRecord record, String fieldName) {
        return Long.parseLong(required(record, fieldName));
    }

    record CliOptions(Path input, Path output) {
        static CliOptions parse(String[] args) {
            Path input = DEFAULT_INPUT;
            Path output = DEFAULT_OUTPUT;

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--input" -> input = readPathArg(args, ++i, "--input");
                    case "--output" -> output = readPathArg(args, ++i, "--output");
                    default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
                }
            }

            return new CliOptions(input, output);
        }

        private static Path readPathArg(String[] args, int index, String option) {
            if (index >= args.length || args[index].isBlank()) {
                throw new IllegalArgumentException("Missing path after " + option);
            }
            return Path.of(args[index]);
        }
    }

    record RepositoryInput(String repoName, String url, List<ModelFile> modelFiles) {
    }

    record ModelFile(
            @JsonProperty("model_file_name") String modelFileName,
            @JsonProperty("full_file_url") String fullFileUrl,
            @JsonProperty("file_size") long fileSize,
            long commits
    ) {
    }

    record IssueStats(int open, int total) {
    }

    static final class RepositoryStats {
        @JsonProperty("repo_name")
        public String repoName;
        public String url;
        public Integer stars;
        public Integer forks;
        public Integer branches;
        public Map<String, Long> languages;
        public Integer contributors;
        public IssueStats issues;
        public Integer pulls;
        @JsonProperty("created_at")
        public String createdAt;
        @JsonProperty("updated_at")
        public String updatedAt;
        public Integer size;
        @JsonProperty("model_files")
        public List<ModelFile> modelFiles;
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String error;

        static RepositoryStats empty(RepositoryInput input) {
            RepositoryStats stats = new RepositoryStats();
            stats.repoName = input.repoName();
            stats.url = input.url();
            stats.modelFiles = input.modelFiles();
            return stats;
        }
    }
}
