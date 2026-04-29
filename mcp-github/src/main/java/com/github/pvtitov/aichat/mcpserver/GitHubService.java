package com.github.pvtitov.aichat.mcpserver;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class GitHubService {

    private static final String REPOS_BASE_DIR = "github-repos";
    private static GitHub github;
    private static boolean githubInitialized = false;

    static {
        initializeGitHub();
    }

    private static void initializeGitHub() {
        String token = System.getenv("GITHUB_TOKEN");
        if (token == null || token.isEmpty()) {
            System.out.println("[MCP GitHub Server: WARNING] GITHUB_TOKEN not set - GitHub API features disabled");
            System.out.println("[MCP GitHub Server: INFO] Local git operations (JGit) will still work");
            githubInitialized = false;
            // Don't throw - just mark as not initialized
        } else {
            try {
                github = new GitHubBuilder().withOAuthToken(token).build();
                githubInitialized = true;
                System.out.println("[MCP GitHub Server: ✓] GitHub API initialized");
                // Ensure repos directory exists
                File reposDir = new File(REPOS_BASE_DIR);
                if (!reposDir.exists()) {
                    reposDir.mkdirs();
                }
            } catch (IOException e) {
                System.out.println("[MCP GitHub Server: ✗] Failed to initialize GitHub: " + e.getMessage());
                githubInitialized = false;
            }
        }
    }

    /**
     * Check if GitHub API is available
     */
    private static boolean isGitHubAvailable() {
        return githubInitialized && github != null;
    }

    /**
     * Clone a GitHub repository
     */
    public static String cloneRepository(String repoUrl, String branch) {
        try {
            // Extract repo name from URL
            String repoName = extractRepoName(repoUrl);
            Path localPath = Paths.get(REPOS_BASE_DIR, repoName);

            // Check if already cloned
            if (Files.exists(Paths.get(localPath.toString(), ".git"))) {
                return "Repository already cloned at: " + localPath.toAbsolutePath();
            }

            Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(localPath.toFile())
                    .setBranch(branch != null ? branch : "main")
                    .call();

            return "Repository cloned successfully at: " + localPath.toAbsolutePath();
        } catch (GitAPIException e) {
            return "Error cloning repository: " + e.getMessage();
        }
    }

    /**
     * Get repository file structure
     */
    public static String getRepoStructure(String repoPath, String maxDepth) {
        try {
            Path path = Paths.get(repoPath);
            if (!Files.exists(path)) {
                return "Path does not exist: " + repoPath;
            }

            int depth = maxDepth != null ? Integer.parseInt(maxDepth) : 2;
            StringBuilder sb = new StringBuilder();
            sb.append("Repository structure for: ").append(repoPath).append("\n");
            sb.append(buildDirectoryTree(path, "", 0, depth));
            return sb.toString();
        } catch (Exception e) {
            return "Error reading repository structure: " + e.getMessage();
        }
    }

    /**
     * Read file contents from repository
     */
    public static String readFileContents(String repoPath, String filePath) {
        try {
            Path fullPath = Paths.get(repoPath, filePath);
            if (!Files.exists(fullPath)) {
                return "File not found: " + filePath;
            }
            String content = Files.readString(fullPath);
            return "Contents of " + filePath + ":\n\n```\n" + content + "\n```";
        } catch (IOException e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    /**
     * List issues from a GitHub repository
     */
    public static String listIssues(String repoFullName, String state) {
        if (!isGitHubAvailable()) {
            return "GitHub API not available (GITHUB_TOKEN not set). Cannot list issues.";
        }
        try {
            GHRepository repo = github.getRepository(repoFullName);
            GHIssueState issueState = state != null ? GHIssueState.valueOf(state.toUpperCase()) : GHIssueState.OPEN;
            List<GHIssue> issues = repo.getIssues(issueState);

            if (issues.isEmpty()) {
                return "No " + state.toLowerCase() + " issues found in " + repoFullName;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Issues in ").append(repoFullName).append(" (").append(state.toLowerCase()).append("):\n\n");
            for (int i = 0; i < Math.min(issues.size(), 20); i++) {
                GHIssue issue = issues.get(i);
                sb.append("#").append(issue.getNumber())
                        .append(" - ").append(issue.getTitle())
                        .append(" (by ").append(issue.getUser().getLogin()).append(")\n");
            }
            if (issues.size() > 20) {
                sb.append("\n... and ").append(issues.size() - 20).append(" more issues");
            }
            return sb.toString();
        } catch (IOException e) {
            return "Error listing issues: " + e.getMessage();
        }
    }

    /**
     * List pull requests from a GitHub repository
     */
    public static String listPullRequests(String repoFullName, String state) {
        if (!isGitHubAvailable()) {
            return "GitHub API not available (GITHUB_TOKEN not set). Cannot list pull requests.";
        }
        try {
            GHRepository repo = github.getRepository(repoFullName);
            GHIssueState prState = state != null ? GHIssueState.valueOf(state.toUpperCase()) : GHIssueState.OPEN;
            List<GHPullRequest> prs = repo.getPullRequests(prState);

            if (prs.isEmpty()) {
                return "No " + state.toLowerCase() + " pull requests found in " + repoFullName;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Pull Requests in ").append(repoFullName).append(" (").append(state.toLowerCase()).append("):\n\n");
            for (int i = 0; i < Math.min(prs.size(), 20); i++) {
                GHPullRequest pr = prs.get(i);
                sb.append("#").append(pr.getNumber())
                        .append(" - ").append(pr.getTitle())
                        .append(" (by ").append(pr.getUser().getLogin()).append(")\n");
            }
            if (prs.size() > 20) {
                sb.append("\n... and ").append(prs.size() - 20).append(" more pull requests");
            }
            return sb.toString();
        } catch (IOException e) {
            return "Error listing pull requests: " + e.getMessage();
        }
    }

    /**
     * Get pull request details
     */
    public static String getPullRequestDetails(String repoFullName, String prNumber) {
        if (!isGitHubAvailable()) {
            return "GitHub API not available (GITHUB_TOKEN not set). Cannot get PR details.";
        }
        try {
            GHRepository repo = github.getRepository(repoFullName);
            GHPullRequest pr = repo.getPullRequest(Integer.parseInt(prNumber));

            StringBuilder sb = new StringBuilder();
            sb.append("# ").append(pr.getTitle()).append("\n\n");
            sb.append("**PR #").append(pr.getNumber()).append("**\n");
            sb.append("**State:** ").append(pr.getState()).append("\n");
            sb.append("**Author:** ").append(pr.getUser().getLogin()).append("\n");
            sb.append("**Created:** ").append(pr.getCreatedAt()).append("\n");
            sb.append("**Source Branch:** ").append(pr.getHead().getRef()).append("\n");
            sb.append("**Target Branch:** ").append(pr.getBase().getRef()).append("\n\n");
            sb.append("## Description\n\n").append(pr.getBody() != null ? pr.getBody() : "No description");
            return sb.toString();
        } catch (IOException e) {
            return "Error getting PR details: " + e.getMessage();
        }
    }

    /**
     * Create a pull request
     */
    public static String createPullRequest(String repoFullName, String title, String body, String head, String base) {
        if (!isGitHubAvailable()) {
            return "GitHub API not available (GITHUB_TOKEN not set). Cannot create pull requests.";
        }
        try {
            GHRepository repo = github.getRepository(repoFullName);
            GHPullRequest pr = repo.createPullRequest(title, head, base, body);

            return "Pull request created successfully:\n" +
                    "**#" + pr.getNumber() + " - " + pr.getTitle() + "**\n" +
                    "URL: " + pr.getHtmlUrl().toString();
        } catch (IOException e) {
            return "Error creating pull request: " + e.getMessage();
        }
    }

    /**
     * Search repositories
     */
    public static String searchRepositories(String query, String language, String sort) {
        if (!isGitHubAvailable()) {
            return "GitHub API not available (GITHUB_TOKEN not set). Cannot search repositories.";
        }
        try {
            StringBuilder searchQuery = new StringBuilder(query);
            if (language != null && !language.isEmpty()) {
                searchQuery.append(" language:").append(language);
            }

            var search = github.searchRepositories().q(searchQuery.toString());
            if ("forks".equals(sort)) {
                search = search.sort(org.kohsuke.github.GHRepositorySearchBuilder.Sort.FORKS);
            } else if ("updated".equals(sort)) {
                search = search.sort(org.kohsuke.github.GHRepositorySearchBuilder.Sort.UPDATED);
            } else {
                search = search.sort(org.kohsuke.github.GHRepositorySearchBuilder.Sort.STARS);
            }
            var result = search.list();

            if (result.getTotalCount() == 0) {
                return "No repositories found matching query: " + query;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Search results for: ").append(query).append("\n\n");
            int count = 0;
            for (GHRepository repo : result) {
                if (count >= 15) break;
                sb.append("**").append(repo.getFullName()).append("**\n");
                sb.append("  Description: ").append(repo.getDescription() != null ? repo.getDescription() : "N/A").append("\n");
                sb.append("  Stars: ").append(repo.getStargazersCount()).append("\n");
                sb.append("  Language: ").append(repo.getLanguage() != null ? repo.getLanguage() : "N/A").append("\n");
                sb.append("  URL: ").append(repo.getHtmlUrl()).append("\n\n");
                count++;
            }
            if (result.getTotalCount() > 15) {
                sb.append("... and ").append(result.getTotalCount() - 15).append(" more results");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error searching repositories: " + e.getMessage();
        }
    }

    /**
     * Get README content from a repository
     */
    public static String getReadme(String repoFullName) {
        if (!isGitHubAvailable()) {
            return "GitHub API not available (GITHUB_TOKEN not set). Cannot get README.";
        }
        try {
            GHRepository repo = github.getRepository(repoFullName);
            java.io.InputStream is = repo.getReadme().read();
            String readme = new String(is.readAllBytes());
            return "README for " + repoFullName + ":\n\n" + readme;
        } catch (IOException e) {
            return "Error getting README: " + e.getMessage();
        }
    }

    /**
     * Commit and push changes to a repository
     */
    public static String commitAndPush(String repoPath, String commitMessage, String filePath) {
        try {
            File gitDir = Paths.get(repoPath, ".git").toFile();
            if (!gitDir.exists()) {
                return "Not a git repository: " + repoPath;
            }

            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            Repository repository = builder.setGitDir(gitDir).build();
            Git git = new Git(repository);

            // Add file
            git.add().addFilepattern(filePath).call();

            // Commit
            git.commit().setMessage(commitMessage).call();

            // Push
            git.push().call();

            return "Changes committed and pushed successfully: " + commitMessage;
        } catch (GitAPIException | IOException e) {
            return "Error committing and pushing: " + e.getMessage();
        }
    }

    /**
     * Create a new branch
     */
    public static String createBranch(String repoPath, String branchName) {
        try {
            File gitDir = Paths.get(repoPath, ".git").toFile();
            if (!gitDir.exists()) {
                return "Not a git repository: " + repoPath;
            }

            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            Repository repository = builder.setGitDir(gitDir).build();
            Git git = new Git(repository);

            git.checkout().setCreateBranch(true).setName(branchName).call();

            return "Branch created successfully: " + branchName;
        } catch (GitAPIException | IOException e) {
            return "Error creating branch: " + e.getMessage();
        }
    }

    /**
     * List commits for a repository
     */
    public static String listCommits(String repoPath, int maxCount) {
        try {
            File gitDir = Paths.get(repoPath, ".git").toFile();
            if (!gitDir.exists()) {
                return "Not a git repository: " + repoPath;
            }

            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            Repository repository = builder.setGitDir(gitDir).build();
            Git git = new Git(repository);

            Iterable<org.eclipse.jgit.revwalk.RevCommit> commits = git.log().setMaxCount(maxCount).call();
            StringBuilder sb = new StringBuilder();
            sb.append("Recent commits in: ").append(repoPath).append("\n\n");
            
            int count = 0;
            for (org.eclipse.jgit.revwalk.RevCommit commit : commits) {
                if (count >= maxCount) break;
                sb.append("Hash: ").append(commit.getName().substring(0, 7)).append("\n");
                sb.append("Author: ").append(commit.getAuthorIdent().getName()).append("\n");
                sb.append("Date: ").append(commit.getCommitterIdent().getWhen()).append("\n");
                sb.append("Message: ").append(commit.getShortMessage()).append("\n");
                sb.append("\n");
                count++;
            }
            
            return sb.toString();
        } catch (GitAPIException | IOException e) {
            return "Error listing commits: " + e.getMessage();
        }
    }

    /**
     * List branches for a repository
     */
    public static String listBranches(String repoPath) {
        try {
            File gitDir = Paths.get(repoPath, ".git").toFile();
            if (!gitDir.exists()) {
                return "Not a git repository: " + repoPath;
            }

            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            Repository repository = builder.setGitDir(gitDir).build();
            Git git = new Git(repository);

            List<org.eclipse.jgit.lib.Ref> branches = git.branchList().call();
            StringBuilder sb = new StringBuilder();
            sb.append("Branches in: ").append(repoPath).append("\n\n");
            
            for (org.eclipse.jgit.lib.Ref branch : branches) {
                String name = branch.getName();
                if (name.startsWith("refs/heads/")) {
                    name = name.substring("refs/heads/".length());
                }
                sb.append("- ").append(name).append("\n");
            }
            
            return sb.toString();
        } catch (GitAPIException | IOException e) {
            return "Error listing branches: " + e.getMessage();
        }
    }

    /**
     * Helper: extract repo name from URL
     */
    private static String extractRepoName(String repoUrl) {
        // Handle both HTTPS and SSH URLs
        if (repoUrl.endsWith(".git")) {
            repoUrl = repoUrl.substring(0, repoUrl.length() - 4);
        }
        String[] parts = repoUrl.split("/");
        return parts[parts.length - 2] + "/" + parts[parts.length - 1];
    }

    /**
     * Helper: build directory tree string
     */
    private static String buildDirectoryTree(Path path, String prefix, int currentDepth, int maxDepth) {
        if (currentDepth > maxDepth) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        File[] files = path.toFile().listFiles();
        if (files == null) {
            return "";
        }

        for (int i = 0; i < files.length; i++) {
            // Skip hidden files and .git directory
            if (files[i].getName().startsWith(".") || files[i].getName().equals("target")) {
                continue;
            }

            boolean isLast = (i == files.length - 1);
            sb.append(prefix).append(isLast ? "└── " : "├── ").append(files[i].getName()).append("\n");

            if (files[i].isDirectory()) {
                sb.append(buildDirectoryTree(files[i].toPath(), prefix + (isLast ? "    " : "│   "), currentDepth + 1, maxDepth));
            }
        }
        return sb.toString();
    }
}
