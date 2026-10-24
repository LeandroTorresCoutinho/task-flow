Feature: Update and delete a task

  Scenario: Owner updates and deletes a task
    Given url karate.properties['task.base-url']
    And path 'api', 'v1', 'tasks'
    And header Authorization = 'Bearer user-a-token'
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
    * def taskId = response.id
    * def createdAt = response.createdAt
    * def originalVersion = response.version

    Given url karate.properties['task.base-url']
    And path 'api', 'v1', 'tasks', taskId
    And header Authorization = 'Bearer user-a-token'
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
    And match response.description == 'Deploy the completed release'
    And match response.status == 'DONE'
    And match response.dueDate == '2026-11-01'
    And match response.createdAt == createdAt
    And match response.updatedAt == '#string'
    And match response.version == originalVersion + 1
    * def updatedTask = response

    Given url karate.properties['task.base-url']
    And path 'api', 'v1', 'tasks', taskId
    And header Authorization = 'Bearer user-a-token'
    When method get
    Then status 200
    And match response == updatedTask

    Given url karate.properties['task.base-url']
    And path 'api', 'v1', 'tasks', taskId
    And header Authorization = 'Bearer user-a-token'
    When method delete
    Then status 204

    Given url karate.properties['task.base-url']
    And path 'api', 'v1', 'tasks', taskId
    And header Authorization = 'Bearer user-a-token'
    When method get
    Then status 404
    And match response.code == 'TASK_NOT_FOUND'

  Scenario: Stale update is rejected
    Given url karate.properties['task.base-url']
    And path 'api', 'v1', 'tasks'
    And header Authorization = 'Bearer user-a-token'
    And request
      """
      {
        "title": "Concurrent task",
        "description": "Test optimistic locking",
        "status": "TODO",
        "dueDate": "2026-10-30"
      }
      """
    When method post
    Then status 201
    * def taskId = response.id
    * def originalVersion = response.version

    Given url karate.properties['task.base-url']
    And path 'api', 'v1', 'tasks', taskId
    And header Authorization = 'Bearer user-a-token'
    And request
      """
      {
        "title": "First update",
        "description": "This update wins",
        "status": "IN_PROGRESS",
        "dueDate": "2026-10-31",
        "version": "#(originalVersion)"
      }
      """
    When method put
    Then status 200
    And match response.version == originalVersion + 1

    Given url karate.properties['task.base-url']
    And path 'api', 'v1', 'tasks', taskId
    And header Authorization = 'Bearer user-a-token'
    And request
      """
      {
        "title": "Stale update",
        "description": "This update must fail",
        "status": "DONE",
        "dueDate": "2026-11-01",
        "version": "#(originalVersion)"
      }
      """
    When method put
    Then status 409
    And match response.code == 'STALE_TASK_VERSION'
    And match response.message == 'Task was modified by another request'

  Scenario: Another user cannot update or delete the task
    Given url karate.properties['task.base-url']
    And path 'api', 'v1', 'tasks'
    And header Authorization = 'Bearer user-a-token'
    And request
      """
      {
        "title": "Private task",
        "description": "Visible only to its owner",
        "status": "TODO",
        "dueDate": "2026-10-31"
      }
      """
    When method post
    Then status 201
    * def taskId = response.id
    * def taskVersion = response.version

    Given url karate.properties['task.base-url']
    And path 'api', 'v1', 'tasks', taskId
    And header Authorization = 'Bearer user-b-token'
    And request
      """
      {
        "title": "Unauthorized update",
        "description": "Must not be persisted",
        "status": "DONE",
        "dueDate": "2026-11-01",
        "version": "#(taskVersion)"
      }
      """
    When method put
    Then status 404
    And match response.code == 'TASK_NOT_FOUND'

    Given url karate.properties['task.base-url']
    And path 'api', 'v1', 'tasks', taskId
    And header Authorization = 'Bearer user-b-token'
    When method delete
    Then status 404
    And match response.code == 'TASK_NOT_FOUND'

    Given url karate.properties['task.base-url']
    And path 'api', 'v1', 'tasks', taskId
    And header Authorization = 'Bearer user-a-token'
    When method get
    Then status 200
    And match response.title == 'Private task'
