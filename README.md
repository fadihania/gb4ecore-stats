# gb4ecore-stats

`gb4ecore-stats` collects GitHub repository statistics for Ecore model repositories listed in a CSV file. The project is intended to support Model-Driven Engineering research datasets by enriching model file records with repository-level metadata.

## Requirements

- JDK 21
- Maven
- A GitHub personal access token exposed as `GITHUB_TOKEN`

The token is required because the collector queries many repositories and unauthenticated GitHub API requests are heavily rate-limited.

## Input

By default, the collector reads:

```text
models/ecore-models.csv
```

The CSV is expected to contain these columns:

```text
model_file_name,repository_name,repository_url,full_file_url,file_size,commits
```

`repository_name` must use the GitHub `owner/name` format.

## Usage

Compile and verify the project:

```powershell
mvn test
```

Set the GitHub token in PowerShell:

```powershell
$env:GITHUB_TOKEN="your_token_here"
```

Run the collector:

```powershell
mvn exec:java
```

The default output file is:

```text
out/ecore-models.json
```

You can override the input and output paths:

```powershell
mvn exec:java -Dexec.args="--input models/ecore-models.csv --output out/ecore-models.json"
```

## Output

The output is a JSON array. Each item represents one GitHub repository and includes its model files from the CSV.

```json
[
  {
    "repo_name": "owner/name",
    "url": "https://github.com/owner/name",
    "stars": 100,
    "forks": 5,
    "branches": 10,
    "languages": {
      "Java": 123456
    },
    "contributors": 10,
    "issues": {
      "open": 4,
      "total": 25
    },
    "pulls": 12,
    "created_at": "2022-10-13T14:09:52Z",
    "updated_at": "2026-06-16T19:17:44Z",
    "size": 2098053,
    "model_files": [
      {
        "name": "example.ecore",
        "url": "https://github.com/owner/name/blob/main/model/example.ecore",
        "size": 15285,
        "commits": 26,
        "commit_history": [
          {
            "url": "https://github.com/owner/name/blob/abc123/model/example.ecore",
            "date": "2026-06-16T19:17:44Z",
            "author": "janedoe",
            "message": "Update model"
          }
        ]
      }
    ],
    "error": null
  }
]
```

If a repository cannot be fetched, the collector keeps the repository entry and model files, leaves unavailable stats as `null`, and writes the failure message to `error`.

Each model file includes `commit_history`, ordered from newest to oldest. If commit history cannot be collected for a specific model file, the collector keeps the file entry, writes an empty `commit_history` array, and adds `commit_history_error`.
