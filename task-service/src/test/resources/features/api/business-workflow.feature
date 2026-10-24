Feature: Complete task workflow over the real auth and task APIs

  Scenario: User creates, lists, reads, updates, and deletes a task
    * def user = call read('helpers/register-and-login.feature')
    * def authorization = 'Bearer ' + user.accessToken

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks'
    And header Authorization = authorization
    And request
      """
      {
        "title": "Prepare release",
        "description": "Complete the release checklist",
        "status": "TODO",
        "dueDate": "2026-10-30"
      }
      """
    When method post
    Then status 201
    And match response.id == '#uuid'
    And match response.title == 'Prepare release'
    And match response.status == 'TODO'
    And match response.version == 0
    * def taskId = response.id
    * def createdAt = response.createdAt
    * def originalVersion = response.version
    And match header Location == '/api/v1/tasks/' + taskId

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks'
    And param limit = 20
    And header Authorization = authorization
    When method get
    Then status 200
    And match response[*].id contains taskId

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks', taskId
    And header Authorization = authorization
    When method get
    Then status 200
    And match response.title == 'Prepare release'
    And match response.version == originalVersion

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks', taskId
    And header Authorization = authorization
    And request
      """
      {
        "title": "Ship release",
        "description": "Deploy the completed release",
        "status": "DONE",
        "dueDate": "2026-11-01",
        "version": "#(originalVersion)"
      }
      """
    When method put
    Then status 200
    And match response.id == taskId
    And match response.title == 'Ship release'
    And match response.status == 'DONE'
    And match response.createdAt == createdAt
    And match response.version == originalVersion + 1

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks', taskId
    And header Authorization = authorization
    And request
      """
      {
        "title": "Stale update",
        "description": "This update must not win",
        "status": "IN_PROGRESS",
        "dueDate": "2026-11-02",
        "version": "#(originalVersion)"
      }
      """
    When method put
    Then status 409
    And match response.code == 'STALE_TASK_VERSION'

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks', taskId
    And header Authorization = authorization
    When method delete
    Then status 204

    Given url taskBaseUrl
    And path 'api', 'v1', 'tasks', taskId
    And header Authorization = authorization
    When method get
    Then status 404
    And match response.code == 'TASK_NOT_FOUND'
