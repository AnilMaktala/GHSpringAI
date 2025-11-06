# GitHub Issue Triage Agent

An AI-powered application that automatically categorizes GitHub issues labeled with "pending-triage" and suggests appropriate responses using Spring AI.

## Features

- **Automatic Issue Classification**: Classifies issues into Bug, Feature Request, Usability, or Question
- **AI-Powered Analysis**: Uses Large Language Models (LLM) via Spring AI to analyze issue content
- **Response Suggestions**: Generates contextual response suggestions for each issue
- **Confidence Scoring**: Provides confidence scores and flags low-confidence classifications for manual review
- **Scheduled Execution**: Runs automatically on a configurable schedule
- **Comprehensive Error Handling**: Handles GitHub API rate limits, authentication errors, and LLM failures gracefully
- **JSON Output**: Exports triage results in structured JSON format with summary statistics

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- GitHub Personal Access Token with repository read access
- AWS Account with Bedrock access
- AWS credentials (Access Key ID and Secret Access Key)

## Configuration

The application is configured through environment variables:

### Required Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `GITHUB_OWNER` | GitHub repository owner/organization | `kirodotdev` |
| `GITHUB_REPO` | GitHub repository name | `Kiro` |
| `GITHUB_TOKEN` | GitHub Personal Access Token | `ghp_xxxxx` |
| `AWS_REGION` | AWS region for Bedrock | `us-west-2` |
| `AWS_PROFILE` | AWS profile name from ~/.aws/credentials | `migration-west-2` |

### Optional Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `AI_MODEL` | Bedrock model to use | `us.anthropic.claude-sonnet-4-5-v2:0` |
| `OUTPUT_PATH` | Path for triage results JSON file | `./triage-results.json` |
| `TRIAGE_SCHEDULE` | Cron expression for scheduled execution | `0 0 */6 * * *` (every 6 hours) |

### Alternative: Using Explicit AWS Credentials

If you prefer not to use AWS profiles, you can set explicit credentials:

| Variable | Description | Example |
|----------|-------------|---------|
| `AWS_ACCESS_KEY_ID` | AWS Access Key ID | `AKIAIOSFODNN7EXAMPLE` |
| `AWS_SECRET_ACCESS_KEY` | AWS Secret Access Key | `wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY` |

Note: When using explicit credentials, remove or comment out the `AWS_PROFILE` variable.

### GitHub Token Permissions

Your GitHub token needs the following permissions:
- **Public repositories**: `public_repo` scope
- **Private repositories**: `repo` scope

To create a token:
1. Go to GitHub Settings → Developer settings → Personal access tokens
2. Generate new token (classic)
3. Select appropriate scopes
4. Copy the token and set it as `GITHUB_TOKEN` environment variable

### AWS Bedrock Setup

#### Option 1: Using AWS Profile (Recommended)

The application uses the AWS profile `migration-west-2` by default. Ensure your `~/.aws/credentials` file contains:

```ini
[migration-west-2]
aws_access_key_id = YOUR_ACCESS_KEY
aws_secret_access_key = YOUR_SECRET_KEY
region = us-west-2
```

And your `~/.aws/config` file contains:

```ini
[profile migration-west-2]
region = us-west-2
output = json
```

#### Option 2: Using Explicit Credentials

Alternatively, set `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` environment variables and remove the `AWS_PROFILE` setting.

#### Required Setup Steps

1. **Enable Bedrock Models**:
   - Go to AWS Console → Amazon Bedrock (in us-west-2 region)
   - Navigate to "Model access"
   - Request access to Anthropic Claude models
   - Wait for approval (usually instant for Claude 3)

2. **Ensure IAM Permissions**:
   Your AWS user/role needs these permissions:
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "bedrock:InvokeModel",
        "bedrock:InvokeModelWithResponseStream"
      ],
      "Resource": "arn:aws:bedrock:us-west-2::foundation-model/*"
    }
  ]
}
```

## Build and Run

### Build the Application

```bash
./mvnw clean package
```

### Run with Environment Variables

```bash
export GITHUB_OWNER=kirodotdev
export GITHUB_REPO=Kiro
export GITHUB_TOKEN=ghp_xxxxx
export AWS_REGION=us-west-2
export AWS_PROFILE=migration-west-2

java -jar target/gh-issue-triage-agent-1.0.0.jar
```

### Run with Inline Environment Variables

```bash
GITHUB_OWNER=kirodotdev GITHUB_REPO=Kiro GITHUB_TOKEN=ghp_xxxxx \
  AWS_REGION=us-west-2 AWS_PROFILE=migration-west-2 \
  java -jar target/gh-issue-triage-agent-1.0.0.jar
```

### Run Using .env File

```bash
# Load environment variables from .env file
export $(cat .env | xargs)

# Build and run
./mvnw clean package
java -jar target/gh-issue-triage-agent-1.0.0.jar
```

## Output Format

The application generates a JSON file with the following structure:

```json
{
  "triageRun": {
    "timestamp": "2025-11-04T10:30:00",
    "totalIssues": 5,
    "summary": {
      "Bug": 2,
      "Feature Request": 1,
      "Usability": 1,
      "Question": 1
    }
  },
  "results": [
    {
      "issueNumber": 123,
      "issueTitle": "Application crashes on startup",
      "issueUrl": "https://github.com/owner/repo/issues/123",
      "category": "Bug",
      "confidence": 95,
      "reasoning": "Issue describes unexpected application behavior with error messages",
      "responseSuggestion": "Thank you for reporting this. Could you provide the full error stack trace and your environment details (OS, Java version)?",
      "processedAt": "2025-11-04T10:30:15Z",
      "flaggedForManualReview": false
    }
  ]
}
```

## How It Works

1. **Fetch Issues**: Retrieves all open issues with the "pending-triage" label from the configured repository
2. **Classify**: Uses AI to analyze each issue and classify it into one of four categories
3. **Generate Response**: Creates a contextual response suggestion based on the category
4. **Persist Results**: Saves all triage results to a JSON file with summary statistics
5. **Schedule**: Repeats the process on the configured schedule

## Supported AI Models

The application uses AWS Bedrock with Anthropic Claude models:

- **Claude Sonnet 4.5** (default): `us.anthropic.claude-sonnet-4-5-v2:0` - Latest and most capable model
- **Claude 3.5 Sonnet**: `anthropic.claude-3-5-sonnet-20241022-v2:0` - Previous generation, still very capable
- **Claude 3 Sonnet**: `anthropic.claude-3-sonnet-20240229-v1:0` - Balanced performance and cost
- **Claude 3 Haiku**: `anthropic.claude-3-haiku-20240307-v1:0` - Fastest and most cost-effective

To change the model, set the `AI_MODEL` environment variable.

## Issue Categories

- **Bug**: Technical problems, errors, or unexpected behavior
- **Feature Request**: New functionality or enhancement requests
- **Usability**: User experience issues or interface improvements
- **Question**: Requests for information or clarification

## Error Handling

The application handles various error scenarios:

- **GitHub API Rate Limit**: Logs reset time and exits with code 2
- **Authentication Failure**: Logs error and exits with code 3
- **Network Timeouts**: Retries up to 3 times with exponential backoff
- **LLM Service Unavailable**: Retries up to 3 times with exponential backoff
- **Individual Issue Failures**: Logs error and continues with remaining issues
- **File Write Failures**: Attempts fallback location with timestamp

## Scheduling

By default, the application runs every 6 hours. You can customize the schedule using a cron expression:

```bash
export TRIAGE_SCHEDULE="0 0 */3 * * *"  # Every 3 hours
export TRIAGE_SCHEDULE="0 0 9 * * MON-FRI"  # 9 AM on weekdays
```

## Development

### Project Structure

```
src/main/java/com/example/triage/
├── client/              # GitHub API client
├── config/              # Configuration classes
├── model/               # Domain models
├── scheduler/           # Scheduled task
└── service/             # Business logic
```

### Running Locally

For development, you can disable scheduling and run manually:

```yaml
# application-dev.yml
triage:
  schedule: "-"  # Disable scheduling
```

Then trigger manually through the TriageService bean.

## Troubleshooting

### Configuration Validation Failed

Ensure all required environment variables are set. The application will log specific missing properties.

### GitHub Authentication Failed

- Verify your `GITHUB_TOKEN` is valid and not expired
- Check that the token has the required permissions
- Ensure the token is correctly set in the environment

### AWS Bedrock Errors

- Verify your AWS profile `migration-west-2` exists in `~/.aws/credentials`
- Check that your AWS credentials are valid and not expired
- Ensure you have enabled model access in Bedrock console (us-west-2 region)
- Verify your IAM user/role has `bedrock:InvokeModel` permission
- Check the model ID is correct (e.g., `anthropic.claude-3-sonnet-20240229-v1:0`)
- Confirm Bedrock is available in us-west-2 region
- Try running `aws bedrock list-foundation-models --region us-west-2 --profile migration-west-2` to verify access

### No Issues Found

If no issues are found, verify:
- The repository has issues with the "pending-triage" label
- The label name matches exactly (case-sensitive)
- Your token has access to the repository

## License

This project is provided as-is for demonstration purposes.
