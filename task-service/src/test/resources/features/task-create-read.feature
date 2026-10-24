Feature: Create and read a task

  Scenario: Authenticated user creates and reads a task
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
    And match response.id == '#uuid'
    And match response.title == 'Prepare release'
    And match response.description == 'Complete the release checklist'
    And match response.status == 'TODO'
    And match response.dueDate == '2026-10-30'
    And match response.createdAt == '#string'
    And match response.updatedAt == '#string'
    * def taskId = response.id
    * def createdTask = response
    And match header Location == '/api/v1/tasks/' + taskId

    Given url karate.properties['task.base-url']
    And path 'api', 'v1', 'tasks', taskId
    And header Authorization = 'Bearer user-a-token'
    When method get
    Then status 200
    And match response == createdTask

  Scenario: Another user cannot read the task
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

    Given url karate.properties['task.base-url']
    And path 'api', 'v1', 'tasks', taskId
    And header Authorization = 'Bearer user-b-token'
    When method get
    Then status 404
    And match response.code == 'TASK_NOT_FOUND'
    And match response.message == 'Task not found'
