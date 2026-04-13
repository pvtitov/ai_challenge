package com.github.pvtitov.aichat.mcpserver;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
public class McpServerConfig {

    @Bean
    public WebMvcSseServerTransportProvider webMvcSseServerTransportProvider() {
        return WebMvcSseServerTransportProvider.builder()
                .messageEndpoint("/mcp/message")
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> mcpRouterFunction(WebMvcSseServerTransportProvider transportProvider) {
        return transportProvider.getRouterFunction();
    }

    @Bean
    public io.modelcontextprotocol.server.McpSyncServer mcpSyncServer(WebMvcSseServerTransportProvider transportProvider) {

        List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();

        // Tool 1: clone_repository
        McpSchema.JsonSchema cloneRepoSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "repo_url", Map.of("type", "string", "description", "GitHub repository URL to clone (e.g., https://github.com/user/repo)"),
                        "branch", Map.of("type", "string", "description", "Branch to clone (default: main)")
                ),
                List.of("repo_url"),
                null,
                null,
                null
        );
        McpSchema.Tool cloneRepoTool = McpSchema.Tool.builder()
                .name("clone_repository")
                .description("Clone a GitHub repository to the local github-repos directory. Use this to get a copy of any repository for exploration or modification.")
                .inputSchema(cloneRepoSchema)
                .build();
        tools.add(new McpServerFeatures.SyncToolSpecification(cloneRepoTool, (exchange, request) -> {
            try {
                String repoUrl = (String) request.arguments().get("repo_url");
                String branch = (String) request.arguments().get("branch");

                if (repoUrl == null || repoUrl.trim().isEmpty()) {
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Error: repo_url is required")),
                            true, null, null
                    );
                }

                String result = GitHubService.cloneRepository(repoUrl.trim(), branch);
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(result)),
                        false, null, null
                );
            } catch (Exception e) {
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error cloning repository: " + e.getMessage())),
                        true, null, null
                );
            }
        }));

        // Tool 2: get_repo_structure
        McpSchema.JsonSchema repoStructureSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "repo_path", Map.of("type", "string", "description", "Local path to the repository (e.g., github-repos/user/repo)"),
                        "max_depth", Map.of("type", "string", "description", "Maximum depth to traverse (default: 2)")
                ),
                List.of("repo_path"),
                null,
                null,
                null
        );
        McpSchema.Tool repoStructureTool = McpSchema.Tool.builder()
                .name("get_repo_structure")
                .description("Get the file/directory structure of a cloned repository. Useful for understanding the codebase layout.")
                .inputSchema(repoStructureSchema)
                .build();
        tools.add(new McpServerFeatures.SyncToolSpecification(repoStructureTool, (exchange, request) -> {
            try {
                String repoPath = (String) request.arguments().get("repo_path");
                String maxDepth = (String) request.arguments().get("max_depth");

                if (repoPath == null || repoPath.trim().isEmpty()) {
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Error: repo_path is required")),
                            true, null, null
                    );
                }

                String result = GitHubService.getRepoStructure(repoPath.trim(), maxDepth);
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(result)),
                        false, null, null
                );
            } catch (Exception e) {
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error getting repo structure: " + e.getMessage())),
                        true, null, null
                );
            }
        }));

        // Tool 3: read_file_contents
        McpSchema.JsonSchema readFileSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "repo_path", Map.of("type", "string", "description", "Local path to the repository"),
                        "file_path", Map.of("type", "string", "description", "Path to the file within the repository")
                ),
                List.of("repo_path", "file_path"),
                null,
                null,
                null
        );
        McpSchema.Tool readFileTool = McpSchema.Tool.builder()
                .name("read_file_contents")
                .description("Read the contents of a specific file in a cloned repository. Use this to examine code files, configs, documentation, etc.")
                .inputSchema(readFileSchema)
                .build();
        tools.add(new McpServerFeatures.SyncToolSpecification(readFileTool, (exchange, request) -> {
            try {
                String repoPath = (String) request.arguments().get("repo_path");
                String filePath = (String) request.arguments().get("file_path");

                if (repoPath == null || repoPath.trim().isEmpty()) {
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Error: repo_path is required")),
                            true, null, null
                    );
                }
                if (filePath == null || filePath.trim().isEmpty()) {
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Error: file_path is required")),
                            true, null, null
                    );
                }

                String result = GitHubService.readFileContents(repoPath.trim(), filePath.trim());
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(result)),
                        false, null, null
                );
            } catch (Exception e) {
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error reading file: " + e.getMessage())),
                        true, null, null
                );
            }
        }));

        // Tool 4: list_issues
        McpSchema.JsonSchema listIssuesSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "repo_full_name", Map.of("type", "string", "description", "Full repository name in format owner/repo (e.g., spring-projects/spring-boot)"),
                        "state", Map.of("type", "string", "description", "Issue state: open, closed, or all (default: open)")
                ),
                List.of("repo_full_name"),
                null,
                null,
                null
        );
        McpSchema.Tool listIssuesTool = McpSchema.Tool.builder()
                .name("list_issues")
                .description("List issues from a GitHub repository. Useful for tracking bugs, feature requests, and tasks.")
                .inputSchema(listIssuesSchema)
                .build();
        tools.add(new McpServerFeatures.SyncToolSpecification(listIssuesTool, (exchange, request) -> {
            try {
                String repoFullName = (String) request.arguments().get("repo_full_name");
                String state = (String) request.arguments().get("state");

                if (repoFullName == null || repoFullName.trim().isEmpty()) {
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Error: repo_full_name is required")),
                            true, null, null
                    );
                }

                String result = GitHubService.listIssues(repoFullName.trim(), state);
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(result)),
                        false, null, null
                );
            } catch (Exception e) {
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error listing issues: " + e.getMessage())),
                        true, null, null
                );
            }
        }));

        // Tool 5: list_pull_requests
        McpSchema.JsonSchema listPRSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "repo_full_name", Map.of("type", "string", "description", "Full repository name in format owner/repo"),
                        "state", Map.of("type", "string", "description", "PR state: open, closed, or all (default: open)")
                ),
                List.of("repo_full_name"),
                null,
                null,
                null
        );
        McpSchema.Tool listPRTool = McpSchema.Tool.builder()
                .name("list_pull_requests")
                .description("List pull requests from a GitHub repository. Useful for reviewing code changes and tracking contributions.")
                .inputSchema(listPRSchema)
                .build();
        tools.add(new McpServerFeatures.SyncToolSpecification(listPRTool, (exchange, request) -> {
            try {
                String repoFullName = (String) request.arguments().get("repo_full_name");
                String state = (String) request.arguments().get("state");

                if (repoFullName == null || repoFullName.trim().isEmpty()) {
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Error: repo_full_name is required")),
                            true, null, null
                    );
                }

                String result = GitHubService.listPullRequests(repoFullName.trim(), state);
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(result)),
                        false, null, null
                );
            } catch (Exception e) {
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error listing pull requests: " + e.getMessage())),
                        true, null, null
                );
            }
        }));

        // Tool 6: get_pull_request_details
        McpSchema.JsonSchema prDetailsSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "repo_full_name", Map.of("type", "string", "description", "Full repository name in format owner/repo"),
                        "pr_number", Map.of("type", "string", "description", "Pull request number")
                ),
                List.of("repo_full_name", "pr_number"),
                null,
                null,
                null
        );
        McpSchema.Tool prDetailsTool = McpSchema.Tool.builder()
                .name("get_pull_request_details")
                .description("Get detailed information about a specific pull request including description, branches, and metadata.")
                .inputSchema(prDetailsSchema)
                .build();
        tools.add(new McpServerFeatures.SyncToolSpecification(prDetailsTool, (exchange, request) -> {
            try {
                String repoFullName = (String) request.arguments().get("repo_full_name");
                String prNumber = (String) request.arguments().get("pr_number");

                if (repoFullName == null || repoFullName.trim().isEmpty()) {
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Error: repo_full_name is required")),
                            true, null, null
                    );
                }
                if (prNumber == null || prNumber.trim().isEmpty()) {
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Error: pr_number is required")),
                            true, null, null
                    );
                }

                String result = GitHubService.getPullRequestDetails(repoFullName.trim(), prNumber.trim());
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(result)),
                        false, null, null
                );
            } catch (Exception e) {
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error getting PR details: " + e.getMessage())),
                        true, null, null
                );
            }
        }));

        // Tool 7: create_pull_request
        McpSchema.JsonSchema createPRSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "repo_full_name", Map.of("type", "string", "description", "Full repository name in format owner/repo"),
                        "title", Map.of("type", "string", "description", "Title of the pull request"),
                        "body", Map.of("type", "string", "description", "Description/body of the pull request"),
                        "head", Map.of("type", "string", "description", "Source branch name (the branch with your changes)"),
                        "base", Map.of("type", "string", "description", "Target branch name (the branch to merge into, usually main or master)")
                ),
                List.of("repo_full_name", "title", "head", "base"),
                null,
                null,
                null
        );
        McpSchema.Tool createPRTool = McpSchema.Tool.builder()
                .name("create_pull_request")
                .description("Create a new pull request. Use this after making changes on a branch to propose them for merging.")
                .inputSchema(createPRSchema)
                .build();
        tools.add(new McpServerFeatures.SyncToolSpecification(createPRTool, (exchange, request) -> {
            try {
                String repoFullName = (String) request.arguments().get("repo_full_name");
                String title = (String) request.arguments().get("title");
                String body = (String) request.arguments().get("body");
                String head = (String) request.arguments().get("head");
                String base = (String) request.arguments().get("base");

                if (repoFullName == null || repoFullName.trim().isEmpty()) {
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Error: repo_full_name is required")),
                            true, null, null
                    );
                }
                if (title == null || title.trim().isEmpty()) {
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Error: title is required")),
                            true, null, null
                    );
                }
                if (head == null || head.trim().isEmpty()) {
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Error: head branch is required")),
                            true, null, null
                    );
                }
                if (base == null || base.trim().isEmpty()) {
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Error: base branch is required")),
                            true, null, null
                    );
                }

                String result = GitHubService.createPullRequest(repoFullName.trim(), title.trim(), 
                        body != null ? body.trim() : "", head.trim(), base.trim());
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(result)),
                        false, null, null
                );
            } catch (Exception e) {
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error creating pull request: " + e.getMessage())),
                        true, null, null
                );
            }
        }));

        // Tool 8: search_repositories
        McpSchema.JsonSchema searchSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "query", Map.of("type", "string", "description", "Search query (e.g., machine learning, spring boot, etc.)"),
                        "language", Map.of("type", "string", "description", "Filter by programming language (e.g., java, python, javascript)"),
                        "sort", Map.of("type", "string", "description", "Sort by: stars, forks, or updated (default: stars)")
                ),
                List.of("query"),
                null,
                null,
                null
        );
        McpSchema.Tool searchTool = McpSchema.Tool.builder()
                .name("search_repositories")
                .description("Search for GitHub repositories by query, language, and sort criteria. Returns top results with metadata.")
                .inputSchema(searchSchema)
                .build();
        tools.add(new McpServerFeatures.SyncToolSpecification(searchTool, (exchange, request) -> {
            try {
                String query = (String) request.arguments().get("query");
                String language = (String) request.arguments().get("language");
                String sort = (String) request.arguments().get("sort");

                if (query == null || query.trim().isEmpty()) {
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Error: query is required")),
                            true, null, null
                    );
                }

                String result = GitHubService.searchRepositories(query.trim(), language, sort);
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(result)),
                        false, null, null
                );
            } catch (Exception e) {
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error searching repositories: " + e.getMessage())),
                        true, null, null
                );
            }
        }));

        // Tool 9: get_readme
        McpSchema.JsonSchema readmeSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "repo_full_name", Map.of("type", "string", "description", "Full repository name in format owner/repo")
                ),
                List.of("repo_full_name"),
                null,
                null,
                null
        );
        McpSchema.Tool readmeTool = McpSchema.Tool.builder()
                .name("get_readme")
                .description("Get the README content of a GitHub repository. Useful for quick project overview and setup instructions.")
                .inputSchema(readmeSchema)
                .build();
        tools.add(new McpServerFeatures.SyncToolSpecification(readmeTool, (exchange, request) -> {
            try {
                String repoFullName = (String) request.arguments().get("repo_full_name");

                if (repoFullName == null || repoFullName.trim().isEmpty()) {
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Error: repo_full_name is required")),
                            true, null, null
                    );
                }

                String result = GitHubService.getReadme(repoFullName.trim());
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(result)),
                        false, null, null
                );
            } catch (Exception e) {
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error getting README: " + e.getMessage())),
                        true, null, null
                );
            }
        }));

        // Tool 10: commit_and_push
        McpSchema.JsonSchema commitSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "repo_path", Map.of("type", "string", "description", "Local path to the repository"),
                        "commit_message", Map.of("type", "string", "description", "Commit message describing the changes"),
                        "file_path", Map.of("type", "string", "description", "File path to commit (relative to repo root, use '.' for all changes)")
                ),
                List.of("repo_path", "commit_message", "file_path"),
                null,
                null,
                null
        );
        McpSchema.Tool commitTool = McpSchema.Tool.builder()
                .name("commit_and_push")
                .description("Commit and push changes from a local repository. Use this to save and upload your modifications to GitHub.")
                .inputSchema(commitSchema)
                .build();
        tools.add(new McpServerFeatures.SyncToolSpecification(commitTool, (exchange, request) -> {
            try {
                String repoPath = (String) request.arguments().get("repo_path");
                String commitMessage = (String) request.arguments().get("commit_message");
                String filePath = (String) request.arguments().get("file_path");

                if (repoPath == null || repoPath.trim().isEmpty()) {
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Error: repo_path is required")),
                            true, null, null
                    );
                }
                if (commitMessage == null || commitMessage.trim().isEmpty()) {
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Error: commit_message is required")),
                            true, null, null
                    );
                }
                if (filePath == null || filePath.trim().isEmpty()) {
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Error: file_path is required")),
                            true, null, null
                    );
                }

                String result = GitHubService.commitAndPush(repoPath.trim(), commitMessage.trim(), filePath.trim());
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(result)),
                        false, null, null
                );
            } catch (Exception e) {
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error committing and pushing: " + e.getMessage())),
                        true, null, null
                );
            }
        }));

        // Tool 11: create_branch
        McpSchema.JsonSchema branchSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "repo_path", Map.of("type", "string", "description", "Local path to the repository"),
                        "branch_name", Map.of("type", "string", "description", "Name of the new branch to create")
                ),
                List.of("repo_path", "branch_name"),
                null,
                null,
                null
        );
        McpSchema.Tool branchTool = McpSchema.Tool.builder()
                .name("create_branch")
                .description("Create a new branch in a local repository. Use this before making changes to propose them later via pull requests.")
                .inputSchema(branchSchema)
                .build();
        tools.add(new McpServerFeatures.SyncToolSpecification(branchTool, (exchange, request) -> {
            try {
                String repoPath = (String) request.arguments().get("repo_path");
                String branchName = (String) request.arguments().get("branch_name");

                if (repoPath == null || repoPath.trim().isEmpty()) {
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Error: repo_path is required")),
                            true, null, null
                    );
                }
                if (branchName == null || branchName.trim().isEmpty()) {
                    return new McpSchema.CallToolResult(
                            List.of(new McpSchema.TextContent("Error: branch_name is required")),
                            true, null, null
                    );
                }

                String result = GitHubService.createBranch(repoPath.trim(), branchName.trim());
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(result)),
                        false, null, null
                );
            } catch (Exception e) {
                return new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error creating branch: " + e.getMessage())),
                        true, null, null
                );
            }
        }));

        return McpServer.sync(transportProvider)
                .serverInfo("GitHub MCP Server", "1.0.0")
                .instructions("This MCP server provides GitHub functionality for repository management and collaboration. Use clone_repository to get repos, get_repo_structure and read_file_contents to explore code, search_repositories to find projects, list_issues and list_pull_requests to track activity, create_pull_request to contribute, and commit_and_push to save changes.")
                .tools(tools)
                .build();
    }
}
