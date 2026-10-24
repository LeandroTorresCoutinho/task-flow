@ignore
Feature: Register and log in a unique user

  Scenario:
    * def email = 'user-' + java.util.UUID.randomUUID() + '@example.com'
    * def password = 'Password123!'

    Given url authBaseUrl
    And path 'api', 'v1', 'auth', 'register'
    And request { email: '#(email)', password: '#(password)' }
    When method post
    Then status 201
    And match response.id == '#uuid'
    And match response.email == email
    * def userId = response.id

    Given url authBaseUrl
    And path 'api', 'v1', 'auth', 'login'
    And request { email: '#(email)', password: '#(password)' }
    When method post
    Then status 200
    And match response.accessToken == '#string'
    And match response.tokenType == 'Bearer'
    And match response.expiresIn == 3600
    * def accessToken = response.accessToken
