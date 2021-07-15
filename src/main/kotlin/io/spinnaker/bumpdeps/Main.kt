package io.spinnaker.bumpdeps

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.CliktHelpFormatter
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.system.exitProcess
import mu.KotlinLogging
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.kohsuke.github.GHIssueState
import org.kohsuke.github.GitHubBuilder

class BumpDeps : CliktCommand() {

    init {
        context { helpFormatter = CliktHelpFormatter(showDefaultValues = true) }
    }

    private val logger = KotlinLogging.logger {}

    companion object {
        const val REF_PREFIX = "refs/tags/v"
        const val GITHUB_OAUTH_TOKEN_ENV_NAME = "GITHUB_OAUTH"
        const val MAVEN_REPO_USERNAME_ENV_NAME = "NEXUS_USERNAME"
        const val MAVEN_REPO_PASSWORD_ENV_NAME = "NEXUS_PASSWORD"
        const val BASE_BRANCH = "master"
    }

    private val version by option("--ref", help = "the release ref triggering this dependency bump").convert { ref ->
        if (!ref.startsWith(REF_PREFIX)) {
            fail("Ref '$ref' is not a valid release ref")
        }
        ref.removePrefix(REF_PREFIX)
    }.required()

    private val key by option(help = "the key in gradle.properties to modify")
        .required()

    private val repositories by option(help = "the comma-separated list of repository names to modify")
        .split(",")
        .required()

    private val repoOwner by option(help = "the owner of the repositories to modify")
        .required()

    private val upstreamOwner by option(help = "the owner of the repositories to send pull requests to")
        .required()

    private val reviewers by option(help = "the comma-separated list of reviewers (prefixed with 'team:' for a team) for the pull request")
        .convert { convertReviewersArg(it) }
        .default(Reviewers(), defaultForHelp = "")

    private val baseBranch by option(help = "the name of the branch to target in pull requests")
        .default(BASE_BRANCH)

    private val oauthToken by lazy {
        System.getenv(GITHUB_OAUTH_TOKEN_ENV_NAME)
            ?: throw UsageError("A GitHub OAuth token must be provided in the $GITHUB_OAUTH_TOKEN_ENV_NAME environment variable")
    }

    private val mavenRepositoryUrl by option(help = "the root URL for the repository where this artifact is stored")
        .convert { it.removeSuffix("/") }

    private val mavenRepositoryUsername by lazy {
        System.getenv(MAVEN_REPO_USERNAME_ENV_NAME) ?: ""
    }

    private val mavenRepositoryPassword by lazy {
        System.getenv(MAVEN_REPO_PASSWORD_ENV_NAME) ?: ""
    }

    private val groupId by option(help = "the groupId for the artifact in the maven repository")

    private val artifactId by option(help = "the artifactId for the artifact in the maven repository")

    private val githubUrl by option(help = "the root URL of the github instance to use")
        .default("https://github.com")

    private val githubApiEndpoint by option(help = "the API endpoint of the github instance to use")
        .default("https://api.github.com")

    override fun run() {
        val repoParent = createTempDirectory()

        waitForArtifact()

        var failures = false
        repositories.forEach { repoName ->
            try {
                val branchName = "autobump-$key-$baseBranch"
                createModifiedBranch(repoParent, repoName, branchName)
                createPullRequest(repoName, branchName)
            } catch (e: Exception) {
                logger.error(e) { "Exception updating repository $repoName" }
                failures = true
            }
        }

        if (failures) {
            exitProcess(1)
        }
    }

    private fun waitForArtifact() {
        if (mavenRepositoryUrl.isNullOrBlank() || groupId.isNullOrBlank() || artifactId.isNullOrBlank()) {
            Thread.sleep(Duration.ofMinutes(10).toMillis())
            return
        }

        val groupPath = groupId!!.replace('.', '/')
        val okHttpClient = OkHttpClient.Builder().build()
        val httpRequestBuilder = Request.Builder()
            .url("$mavenRepositoryUrl/$groupPath/$artifactId/$version/$artifactId-$version.pom")

        if (mavenRepositoryUsername.isNotEmpty() || mavenRepositoryPassword.isNotEmpty()) {
            logger.info { "using credentials" }
            var credential = Credentials.basic(mavenRepositoryUsername, mavenRepositoryPassword)
            httpRequestBuilder.addHeader("Authorization", credential)
        }
        val request = httpRequestBuilder.build()

        val start = Instant.now()
        val retryDelay = Duration.ofSeconds(30)
        val maxTotalDelay = Duration.ofMinutes(10)
        while (true) {
            val response = okHttpClient.newCall(request).execute()
            val elapsed = Duration.between(start, Instant.now())
            if (response.isSuccessful) {
                logger.info { "Found the artifact after ${elapsed.toSeconds()}s" }
                return
            }
            val delayLeft = maxTotalDelay - Duration.between(start, Instant.now())
            if (delayLeft.isNegative) {
                throw RuntimeException("Couldn't find artifact after ${elapsed.toSeconds()}s; giving up.")
            }
            val sleepTime = minOf(delayLeft, retryDelay)
            logger.warn { "Artifact isn't published yet; waiting ${sleepTime.toSeconds()}s" }
            Thread.sleep(sleepTime.toMillis())
        }
    }

    private fun createModifiedBranch(
        repoParent: Path,
        repoName: String,
        branchName: String
    ) {
        val credentialsProvider =
            UsernamePasswordCredentialsProvider("ignored-username", oauthToken)

        val repoRoot = repoParent.resolve(repoName)
        val upstreamUri = "$githubUrl/$upstreamOwner/$repoName"
        logger.info { "Cloning $upstreamUri to $repoRoot" }
        val git = Git.cloneRepository()
            .setCredentialsProvider(credentialsProvider)
            .setURI(upstreamUri)
            .setDirectory(repoRoot.toFile())
            .setBranch(baseBranch)
            .call()
        val gradlePropsFile = repoRoot.resolve("gradle.properties")
        updatePropertiesFile(gradlePropsFile, repoName)
        if (git.branchList().call().map { it.name }.contains(branchName)) {
            git.branchDelete().setBranchNames(branchName).setForce(true).call()
        }
        git.checkout().setName(branchName).setCreateBranch(true).call()
        git.commit().setMessage("chore(dependencies): Autobump $key").setAll(true).call()
        val userUri = "$githubUrl/$repoOwner/$repoName"
        git.remoteAdd().setName("userFork").setUri(URIish(userUri)).call()
        logger.info { "Force-pushing changes to $userUri" }
        git.push()
            .setCredentialsProvider(credentialsProvider)
            .setRemote("userFork")
            .setRefSpecs(RefSpec("$branchName:$branchName"))
            .setForce(true)
            .call()
    }

    private fun updatePropertiesFile(gradlePropsFile: Path, repoName: String) {
        val props = NonDestructivePropertiesEditor(gradlePropsFile)
        val result = props.updateProperty(key, version)

        if (!result.matched) {
            throw IllegalArgumentException("Couldn't locate key $key in $repoName's gradle.properties file")
        }
        if (!result.updated) {
            throw IllegalArgumentException("$repoName's $key is already set to $version")
        }

        props.saveResult(result.lines)
    }

    private fun createPullRequest(repoName: String, branchName: String) {
        val github = GitHubBuilder().withEndpoint(githubApiEndpoint).withOAuthToken(oauthToken).build()
        val githubRepo = github.getRepository("$upstreamOwner/$repoName")

        // If there's already an existing PR, we can just reuse it... we already force-pushed the branch, so it'll
        // automatically update.
        val existingPr = githubRepo.getPullRequests(GHIssueState.OPEN)
            .firstOrNull { pr -> pr.labels.map { label -> label.name }.contains(branchName) }

        if (existingPr != null) {
            logger.info { "Found existing PR for repo $repoName: ${existingPr.htmlUrl}" }
            return
        }

        logger.info { "Creating pull request for repo $repoName" }
        val pr = githubRepo.createPullRequest(
            /* title= */"chore(dependencies): Autobump $key",
            /* head= */ "$repoOwner:$branchName",
            /* base= */ baseBranch,
            /* body= */ ""
        )

        pr.addLabels(branchName)

        if (reviewers.users.isNotEmpty()) {
            pr.requestReviewers(reviewers.users.map { github.getUser(it) })
        }
        if (reviewers.teams.isNotEmpty()) {
            val upstreamOrg = github.getOrganization(upstreamOwner)
            pr.requestTeamReviewers(reviewers.teams.map { upstreamOrg.getTeamByName(it) })
        }

        logger.info { "Created pull request for $repoName: ${pr.htmlUrl}" }
    }

    data class Reviewers(val users: Set<String> = setOf(), val teams: Set<String> = setOf())

    private fun convertReviewersArg(reviewersString: String): Reviewers {
        val reviewers = reviewersString.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val teams = reviewers.filter { it.startsWith("team:") }.toSet()
        val users = reviewers - teams
        return Reviewers(users, teams.map { it.removePrefix("team:") }.toSet())
    }

    private fun createTempDirectory(): Path {
        val repoParent = Files.createTempDirectory("bumpdeps-git-")
        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                repoParent.toFile().deleteRecursively()
            }
        })
        return repoParent
    }
}

fun main(args: Array<String>) {
    BumpDeps().main(args)
}
