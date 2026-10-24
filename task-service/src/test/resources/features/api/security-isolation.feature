Feature: Authentication and cross-user task isolation

  Scenario: Another user cannot list, read, update, or delete an owned task
    * def owner = call read('helpers/register-and-login.feature')
    * def other = call read('helpers/register-and-login.feature')
    * def ownerAuthorization = 'Bearer ' + owner.accessToken
    * def otherAuthorization = 'Bearer ' + other.accessToken

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks'
    And header Authorization = ownerAuthorization
    And request { title: 'Private task', description: 'Owner only', status: 'TODO', dueDate: '2026-10-31' }
    When method post
    Then status 201
    * def taskId = response.id
    * def version = response.version

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks'
    And header Authorization = otherAuthorization
    When method get
    Then status 200
    And match response[*].id !contains taskId

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks', taskId
    And header Authorization = otherAuthorization
    When method get
    Then status 404
    And match response.code == 'TASK_NOT_FOUND'

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks', taskId
    And header Authorization = otherAuthorization
    And request { title: 'Stolen task', description: 'Must not persist', status: 'DONE', dueDate: '2026-11-01', version: '#(version)' }
    When method put
    Then status 404
    And match response.code == 'TASK_NOT_FOUND'

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks', taskId
    And header Authorization = otherAuthorization
    When method delete
    Then status 404
    And match response.code == 'TASK_NOT_FOUND'

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks', taskId
    And header Authorization = ownerAuthorization
    When method get
    Then status 200
    And match response.title == 'Private task'
    And match response.version == version

  Scenario: Request body cannot assign task ownership
    * def owner = call read('helpers/register-and-login.feature')
    * def other = call read('helpers/register-and-login.feature')
    * def ownerAuthorization = 'Bearer ' + owner.accessToken
    * def otherAuthorization = 'Bearer ' + other.accessToken

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks'
    And header Authorization = otherAuthorization
    And request { ownerId: '#(owner.userId)', title: 'Injected owner', description: 'Must belong to caller', status: 'TODO', dueDate: '2026-10-31' }
    When method post
    Then status 201
    * def taskId = response.id

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks', taskId
    And header Authorization = ownerAuthorization
    When method get
    Then status 404
    And match response.code == 'TASK_NOT_FOUND'

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks', taskId
    And header Authorization = otherAuthorization
    When method get
    Then status 200
    And match response.title == 'Injected owner'

  Scenario: Missing and corrupted access tokens are rejected
    * def user = call read('helpers/register-and-login.feature')
    * def corruptedAuthorization = 'Bearer ' + user.accessToken + 'corrupted'

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks'
    When method get
    Then status 401

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks'
    And header Authorization = corruptedAuthorization
    When method get
    Then status 401

  Scenario: Login does not reveal whether an account exists
    * def user = call read('helpers/register-and-login.feature')

    Given url authBaseUrl
    And path 'api', 'v1', 'auth', 'login'
    And request { email: '#(user.email)', password: 'WrongPassword123!' }
    When method post
    Then status 401
    And match response.code == 'INVALID_CREDENTIALS'
    * def wrongPassword = response

    Given url authBaseUrl
    And path 'api', 'v1', 'auth', 'login'
    And request { email: 'missing-user@example.com', password: 'WrongPassword123!' }
    When method post
    Then status 401
    And match response.code == wrongPassword.code
    And match response.status == wrongPassword.status
    And match response.message == wrongPassword.message
