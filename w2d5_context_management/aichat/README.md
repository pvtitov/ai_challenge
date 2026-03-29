# aichat

This is a minimal and simple yet scalable Java project for a backend for web and mobile clients.

## Setup

### Prerequisites

- Java 11 or later
- Maven 3.2+
- GigaChat API credentials

### Installation

1.  **Set Environment Variable:**
    This application requires GigaChat API credentials. You need to set the `GIGACHAT_API_CREDENTIALS` environment variable. This should be the `Basic` authentication credential string (a Base64 encoded string of your client ID and client secret).

    On macOS/Linux:
    ```sh
    export GIGACHAT_API_CREDENTIALS="YOUR_BASE64_ENCODED_CREDENTIALS"
    ```

    On Windows:
    ```powershell
    $env:GIGACHAT_API_CREDENTIALS="YOUR_BASE64_ENCODED_CREDENTIALS"
    ```
    Replace `YOUR_BASE64_ENCODED_CREDENTIALS` with your actual credentials.

2.  **Clone the repository:**
    ```sh
    git clone <repository-url>
    ```

3.  **Navigate to the project directory:**
    ```sh
    cd aichat
    ```

## Usage

### Build the project

To build the project, run the following command from the `aichat` directory:

```sh
mvn clean install
```

### Run the application

You can run the application using the following command:

```sh
mvn spring-boot:run
```

The application will start on port 8080.

### Accessing the Web Client

Once the application is running, you can access the chat interface by opening your web browser and navigating to:

```
http://localhost:8080
```

You will see a simple chat interface where you can send messages to the AI and see the responses, along with token usage information.