# Asynchronous Kotlin Web Crawler

A high-performance, asynchronous web crawler built with Kotlin Coroutines for efficient, concurrent data extraction. It leverages Redis for distributed job queuing, state management, and polite domain-specific rate-limiting driven by a custom `robots.txt` parser. Cleaned HTML content is processed using Jsoup and persisted into a PostgreSQL database, utilizing `tsvector` data types and GIN indexing to enable robust, native full-text search capabilities across all crawled web pages.

## Key Features

*   **Massively Concurrent:** Utilizes Kotlin Coroutines (`Dispatchers.IO` and `Dispatchers.Default`) to run lightweight workers, maximizing network throughput without thread-blocking.
*   **Polite & Compliant:** Features a custom `robots.txt` parser that caches policies in Redis, strictly obeys `Disallow` directives, and implements token-bucket rate limiting based on `Crawl-delay`.
*   **Distributed Architecture Ready:** Uses Redis as a centralized message broker for the crawl queue (`lpush`/`brpop`) and a shared `visited:urls` set, allowing horizontal scaling across multiple crawler instances.
*   **Native Full-Text Search:** Bypasses basic `LIKE` queries by leveraging PostgreSQL's `tsvector` and `tsquery`. Automatically weights titles over body content (`setweight`) and queries against a fast GIN index.
*   **Automated Migrations:** Uses Flyway to manage database schemas, ensuring the database is always in the correct state before the crawler starts.

## Tech Stack

*   **Language:** Kotlin (JVM 24)
*   **HTML Parsing:** Jsoup
*   **Caching & Queuing:** Redis (Jedis pool)
*   **Database:** PostgreSQL
*   **ORM & Migrations:** JetBrains Exposed (JDBC), Flyway, Hikari

## Prerequisites

Before running the crawler, ensure you have the following services running locally:

1.  **Redis:** Running on `localhost:6379`
2.  **PostgreSQL:** Running on `localhost:5432`
    *   Database Name: `crawler`
    *   Username: `postgres`
    *   Password: `postgres`
*    **These are hardcoded into some of the places and will eventually be changed**

## Getting Started

1.  **Clone the repository:**
    ```bash
    git clone <repository-url>
    cd WebCrawler
    ```

2.  **Build the project:**
    ```bash
    ./gradlew build
    ```

3.  **Run the crawler:**
    The application entry point is `org.example.MainKt`. Flyway will automatically run the initial migration (`V1__create_pages.sql`) to set up the `pages` table and GIN index.
    ```bash
    ./gradlew run
    ```
    *Note: The crawler is currently hardcoded to seed from `https://quotes.toscrape.com/` and limits itself to 1,000 pages or a 10-second idle timeout.*

## Architecture Overview

1.  **Initialization:** The `main` function connects to Postgres and Redis, clears any stale queues, seeds the starting URL, and launches 10 concurrent worker coroutines.
2.  **Extraction:** Workers execute a blocking pop (`brpop`) on the Redis queue. When a URL is received, it is checked against the target domain's `robots.txt` rules and rate limits.
3.  **Processing:** If allowed, the HTML is fetched. Jsoup parses the DOM to extract the title, text content (capped at 100k characters for performance), and all absolute outbound links.
4.  **Storage:** The parsed data is upserted into PostgreSQL using Exposed.
5.  **Discovery:** New, unvisited URLs from the same domain are pushed back into the Redis queue for further crawling.

## Future Enhancements

*   **Ktor Integration:** Implement the Ktor Client (CIO Engine) for the networking layer to leverage idiomatic, fully non-blocking asynchronous HTTP requests natively designed for Kotlin Coroutines.
*   **Dockerization:** Add a `docker-compose.yml` to spin up the required PostgreSQL and Redis instances automatically, ensuring a frictionless setup for new contributors.
*   **Configuration Management:** Extract hardcoded database credentials, Redis hosts, seed URLs, and worker counts into environment variables or a `.conf` file using a library like HOCON or Dotenv.
*   **Reliable Queuing:** Upgrade the Redis `brpop` implementation to `RPOPLPUSH` (or Redis Streams) to create a dead-letter queue. This ensures that if a worker crashes mid-crawl, the URL is not lost.
*   **Advanced URL Normalization:** Implement aggressive URL normalization (stripping fragments `#`, sorting query parameters, normalizing trailing slashes) to prevent the crawler from processing duplicate pages.
*   **Testing Suite:** Introduce JUnit5, MockK, and Testcontainers to spin up ephemeral Postgres/Redis instances for robust integration testing.
*   **Structured Logging:** Replace `println()` statements with a proper SLF4J implementation (e.g., Logback) to provide searchable, leveled logs (INFO, WARN, ERROR) for better observability.
