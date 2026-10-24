Feature: Invalid auth and task input is rejected consistently

  Scenario: Invalid registration and login fields return validation errors
    Given url authBaseUrl
    And path 'api', 'v1', 'auth', 'register'
    And request { email: 'not-an-email', password: 'Password123!' }
    When method post
    Then status 400
    And match response.code == 'VALIDATION_ERROR'

    Given url authBaseUrl
    And path 'api', 'v1', 'auth', 'register'
    And request { email: 'short-password@example.com', password: 'short' }
    When method post
    Then status 400
    And match response.code == 'VALIDATION_ERROR'

    * def longPassword = 'a'.repeat(73)
    Given url authBaseUrl
    And path 'api', 'v1', 'auth', 'register'
    And request { email: 'long-password@example.com', password: '#(longPassword)' }
    When method post
    Then status 400
    And match response.code == 'VALIDATION_ERROR'

    Given url authBaseUrl
    And path 'api', 'v1', 'auth', 'login'
    And request { email: 'not-an-email', password: 'Password123!' }
    When method post
    Then status 400
    And match response.code == 'VALIDATION_ERROR'

    Given url authBaseUrl
    And path 'api', 'v1', 'auth', 'login'
    And request { email: 'valid@example.com', password: '' }
    When method post
    Then status 400
    And match response.code == 'VALIDATION_ERROR'

  Scenario: Invalid task bodies return stable client errors
    * def user = call read('helpers/register-and-login.feature')
    * def authorization = 'Bearer ' + user.accessToken

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks'
    And header Authorization = authorization
    And request { title: '', description: 'Description', status: 'TODO', dueDate: '2026-10-30' }
    When method post
    Then status 400
    And match response.code == 'VALIDATION_ERROR'

    * def longTitle = 'x'.repeat(201)
    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks'
    And header Authorization = authorization
    And request { title: '#(longTitle)', description: 'Description', status: 'TODO', dueDate: '2026-10-30' }
    When method post
    Then status 400
    And match response.code == 'VALIDATION_ERROR'

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks'
    And header Authorization = authorization
    And request { title: 'Missing description', status: 'TODO', dueDate: '2026-10-30' }
    When method post
    Then status 400
    And match response.code == 'VALIDATION_ERROR'

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks'
    And header Authorization = authorization
    And request { title: 'Bad status', description: 'Description', status: 'BLOCKED', dueDate: '2026-10-30' }
    When method post
    Then status 400
    And match response.code == 'INVALID_REQUEST'

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks'
    And header Authorization = authorization
    And request { title: 'Bad date', description: 'Description', status: 'TODO', dueDate: 'not-a-date' }
    When method post
    Then status 400
    And match response.code == 'INVALID_REQUEST'

  Scenario: Invalid task versions, limits, and identifiers return stable client errors
    * def user = call read('helpers/register-and-login.feature')
    * def authorization = 'Bearer ' + user.accessToken

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks'
    And header Authorization = authorization
    And request { title: 'Versioned task', description: 'Description', status: 'TODO', dueDate: '2026-10-30' }
    When method post
    Then status 201
    * def taskId = response.id

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks', taskId
    And header Authorization = authorization
    And request { title: 'Missing version', description: 'Description', status: 'TODO', dueDate: '2026-10-30' }
    When method put
    Then status 400
    And match response.code == 'VALIDATION_ERROR'

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks', taskId
    And header Authorization = authorization
    And request { title: 'Negative version', description: 'Description', status: 'TODO', dueDate: '2026-10-30', version: -1 }
    When method put
    Then status 400
    And match response.code == 'VALIDATION_ERROR'

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks'
    And param limit = 0
    And header Authorization = authorization
    When method get
    Then status 400
    And match response.code == 'VALIDATION_ERROR'

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks'
    And param limit = 101
    And header Authorization = authorization
    When method get
    Then status 400
    And match response.code == 'VALIDATION_ERROR'

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks'
    And param limit = 'not-a-number'
    And header Authorization = authorization
    When method get
    Then status 400
    And match response.code == 'INVALID_REQUEST'

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks', 'not-a-uuid'
    And header Authorization = authorization
    When method get
    Then status 400
    And match response.code == 'INVALID_REQUEST'
