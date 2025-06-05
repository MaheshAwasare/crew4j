# Contributing to JavaAIAgent

We welcome contributions to the JavaAIAgent project! Whether you're fixing a bug, adding a new feature, improving documentation, or just providing feedback, your input is valuable.

Please take a moment to review this document to understand how to contribute effectively.

## Table of Contents

* [Code of Conduct](#code-of-conduct)
* [How Can I Contribute?](#how-can-i-contribute)
    * [Reporting Bugs](#reporting-bugs)
    * [Suggesting Enhancements](#suggesting-enhancements)
    * [Writing Code](#writing-code)
    * [Improving Documentation](#improving-documentation)
* [Setting Up Your Development Environment](#setting-up-your-development-environment)
* [Commit Guidelines](#commit-guidelines)
* [Pull Request Guidelines](#pull-request-guidelines)
* [Code Style](#code-style)
* [Legal](#legal)
* [Contact](#contact)

## Code of Conduct

Please note that this project is released with a [Contributor Code of Conduct](CODE_OF_CONDUCT.md). By participating in this project, you agree to abide by its terms.

## How Can I Contribute?

### Reporting Bugs

* **Check existing issues:** Before opening a new issue, please check if a similar bug has already been reported.
* **Provide detailed information:** When reporting a bug, please include:
    * A clear and concise description of the bug.
    * Steps to reproduce the behavior.
    * Expected behavior.
    * Actual behavior.
    * Your Java version and operating system.
    * Any relevant stack traces or error messages.
    * Screenshots or videos (if applicable).

### Suggesting Enhancements

* **Check existing issues:** Before opening a new issue, please check if a similar enhancement has already been suggested.
* **Clearly describe the enhancement:** Explain why this enhancement would be valuable to the project.
* **Provide examples:** If possible, illustrate how the enhancement would be used or how it would improve the current functionality.

### Writing Code

* **Fork the repository:** Start by forking the `javaaiagent` repository to your GitHub account.
* **Create a new branch:** For each new feature or bug fix, create a new branch from `main`. Use a descriptive branch name (e.g., `feature/add-chatbot-integration` or `fix/null-pointer-exception`).
* **Implement your changes:** Write clear, concise, and well-tested code.
* **Write tests:** Ensure your code is covered by appropriate unit and integration tests.
* **Keep commits small and focused:** Each commit should represent a single logical change.
* **Open a Pull Request:** Once your changes are complete, open a Pull Request (PR) to the `main` branch of the original repository.

### Improving Documentation

* **Identify areas for improvement:** Look for outdated information, unclear explanations, or missing details in the `README.md` or other documentation files.
* **Create a new branch:** Similar to code contributions, create a new branch for your documentation changes.
* **Make your edits:** Clearly and concisely improve the documentation.
* **Open a Pull Request:** Submit your documentation improvements via a Pull Request.

## Setting Up Your Development Environment

1.  **Clone your forked repository:**
    ```bash
    git clone https://github.com/MaheshAwasare/javaagentai.git
    cd javaaiagent
    ```
2.  **Ensure you have Java Development Kit (JDK) installed.** The project currently uses Java 17.
3.  **Choose your IDE:** We recommend IntelliJ IDEA or VS Code with Java extensions.
4.  **Import the project:** Open the `javaaiagent` directory in your IDE as a Maven project. Your IDE should automatically download dependencies.

## Commit Guidelines

Please follow these guidelines for your commit messages:

* **Type:** Use a prefix indicating the type of commit (e.g., `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`).
    * `feat`: A new feature
    * `fix`: A bug fix
    * `docs`: Documentation only changes
    * `style`: Changes that do not affect the meaning of the code (whitespace, formatting, missing semicolons, etc.)
    * `refactor`: A code change that neither fixes a bug nor adds a feature
    * `test`: Adding missing tests or correcting existing tests
    * `chore`: Changes to the build process or auxiliary tools and libraries such as documentation generation
* **Scope (optional):** Briefly describe the part of the codebase affected (e.g., `api`, `core`, `ui`).
* **Subject:** A very brief summary of the commit, in the imperative mood (e.g., "Add user authentication," not "Added user authentication"). Max 50 characters.
* **Body (optional):** A more detailed explanation of the commit.
* **Footer (optional):** Reference issues (e.g., `Closes #123`).

**Example:**
feat(api): Add new endpoint for user profile

This commit introduces a new REST endpoint to retrieve user profile
information based on their ID. It includes validation for the user ID
and proper error handling.

Closes #45
## Pull Request Guidelines

* **One feature/fix per PR:** Each Pull Request should address a single, well-defined feature or bug fix.
* **Descriptive title:** Give your PR a clear and concise title.
* **Detailed description:** In the PR description, explain:
    * What problem does this PR solve?
    * How does it solve it?
    * Any relevant technical details or design choices.
    * References to related issues.
* **Tests:** Ensure all new code is accompanied by appropriate tests, and all existing tests pass.
* **Code Style:** Adhere to the project's code style guidelines (see below).
* **Review:** Be prepared for code review feedback and be responsive to comments.
* **Rebase regularly:** Before submitting your PR and during the review process, rebase your branch on the latest `main` to avoid merge conflicts.

## Code Style

* **Google Java Format:** We generally follow the [Google Java Format](https://github.com/google/google-java-format). Please configure your IDE to use this formatter or run it manually before committing.
* **Consistency:** Maintain consistency with the existing codebase's style.
* **Readability:** Write clean, readable, and well-commented code.

## Legal

By contributing to JavaAIAgent, you agree that your contributions will be licensed under its [LICENSE](LICENSE) file.

## Contact

If you have any questions or requests regarding contributions, please feel free to reach out to:

maheshawasare@gmail.com
