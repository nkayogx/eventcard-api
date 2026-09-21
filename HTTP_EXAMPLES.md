### Example HTTP Requests (REST Client)

This file can be used with IntelliJ IDEA's built-in REST client or other HTTP clients.
Supported files:
- IntelliJ IDEA (requests.http)
- Visual Studio Code (REST Client extension)
- Postman
- cURL

---

## Variables

@baseUrl = http://localhost:8181
@token = your_jwt_token_here

---

## Authentication Endpoints (Public)

### Register New User
POST {{baseUrl}}/api/auth/register
Content-Type: application/json

{
  "name": "John Doe",
  "email": "john@example.com",
  "password": "SecurePassword123"
}

### Login User
POST {{baseUrl}}/api/auth/login
Content-Type: application/json

{
  "email": "john@example.com",
  "password": "SecurePassword123"
}

---

## User Management Endpoints (Protected)

### Get All Users
GET {{baseUrl}}/api/users
Authorization: Bearer {{token}}

### Get User by ID
GET {{baseUrl}}/api/users/1
Authorization: Bearer {{token}}

### Create User
POST {{baseUrl}}/api/users
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "name": "Jane Doe",
  "email": "jane@example.com",
  "password": "SecurePassword456"
}

### Update User
PUT {{baseUrl}}/api/users/1
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "name": "Jane Smith",
  "email": "jane.smith@example.com"
}

### Delete User
DELETE {{baseUrl}}/api/users/1
Authorization: Bearer {{token}}

### Admin Statistics (Admin Only)
GET {{baseUrl}}/api/users/admin/statistics
Authorization: Bearer {{token}}

---

## Student Endpoints (Protected)

### Get All Students
GET {{baseUrl}}/students
Authorization: Bearer {{token}}

### Get Student by ID
GET {{baseUrl}}/students/1
Authorization: Bearer {{token}}

### Create Student
POST {{baseUrl}}/students
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "firstName": "john",
  "lastName": "Doe",
  "age": 20
}

### Search Students by First Name
GET {{baseUrl}}/students/search?firstName=john
Authorization: Bearer {{token}}

---

## Complete Testing Workflow

### 1. Register a User
POST http://localhost:8181/api/auth/register
Content-Type: application/json

{
  "name": "Test User",
  "email": "test@example.com",
  "password": "TestPassword123"
}

# Response:
# {
#   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
#   "email": "test@example.com",
#   "roles": ["ROLE_USER"]
# }

###

### 2. Save token and test user read (SHOULD WORK - has user:read)
GET http://localhost:8181/api/users
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

###

### 3. Try to create a user (SHOULD FAIL - no user:create permission)
POST http://localhost:8181/api/users
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json

{
  "name": "New User",
  "email": "newuser@example.com",
  "password": "Password123"
}

# Expected response:
# 403 Forbidden
# Access Denied: Principal does not have the required authority

###

### 4. Try admin endpoint (SHOULD FAIL - not ROLE_ADMIN)
GET http://localhost:8181/api/users/admin/statistics
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

# Expected response:
# 403 Forbidden
# Access Denied: Principal does not have the required role

###

### 5. Login with the same user
POST http://localhost:8181/api/auth/login
Content-Type: application/json

{
  "email": "test@example.com",
  "password": "TestPassword123"
}

###

### 6. Create a student (SHOULD WORK - has user:create)
POST http://localhost:8181/students
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json

{
  "firstName": "John",
  "lastName": "Doe",
  "age": 20
}

###

### 7. Search for student (SHOULD WORK - has user:read)
GET http://localhost:8181/students/search?firstName=John
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

###

### 8. Try invalid token (SHOULD FAIL)
GET http://localhost:8181/api/users
Authorization: Bearer invalid.token.here

# Expected response:
# 403 Forbidden
# No Authentication object found in SecurityContextHolder

###

### 9. Try without token (SHOULD FAIL)
GET http://localhost:8181/api/users

# Expected response:
# 403 Forbidden

---

## Creating an Admin User (Manual SQL)

### Step 1: Register and login as regular user
POST http://localhost:8181/api/auth/register
Content-Type: application/json

{
  "name": "Admin User",
  "email": "admin@example.com",
  "password": "AdminPassword123"
}

###

### Step 2: Manually assign ROLE_ADMIN in database
# Execute in PostgreSQL:
# 
# BEGIN;
# UPDATE users SET email = 'admin@example.com' WHERE id = (SELECT MAX(id) FROM users);
# INSERT INTO user_roles (user_id, role_id)
# SELECT u.id, r.id FROM users u, roles r 
# WHERE u.email = 'admin@example.com' AND r.name = 'ROLE_ADMIN';
# COMMIT;

###

### Step 3: Now admin user can use all endpoints
POST http://localhost:8181/api/auth/login
Content-Type: application/json

{
  "email": "admin@example.com",
  "password": "AdminPassword123"
}

# Token will now have: ["ROLE_ADMIN", "user:read", "user:create", "user:delete", "admin:all"]

###

### Step 4: Admin can access admin statistics
GET http://localhost:8181/api/users/admin/statistics
Authorization: Bearer {admin_token}

###

### Step 5: Admin can create users
POST http://localhost:8181/api/users
Authorization: Bearer {admin_token}
Content-Type: application/json

{
  "name": "New User",
  "email": "newuser@example.com",
  "password": "Password123"
}

###

### Step 6: Admin can delete users
DELETE http://localhost:8181/api/users/1
Authorization: Bearer {admin_token}

---

## Error Response Examples

### 401 Unauthorized - No token provided
GET http://localhost:8181/api/users

Response:
```
401 Unauthorized
{
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource"
}
```

###

### 403 Forbidden - Insufficient permissions
GET http://localhost:8181/api/users/admin/statistics
Authorization: Bearer {regular_user_token}

Response:
```
403 Forbidden
{
  "error": "Forbidden",
  "message": "Access Denied: Principal does not have the required role: ROLE_ADMIN"
}
```

###

### 400 Bad Request - Invalid credentials
POST http://localhost:8181/api/auth/login
Content-Type: application/json

{
  "email": "nonexistent@example.com",
  "password": "WrongPassword"
}

Response:
```
401 Unauthorized
{
  "error": "Unauthorized",
  "message": "Invalid email or password"
}
```

###

### 409 Conflict - User already exists
POST http://localhost:8181/api/auth/register
Content-Type: application/json

{
  "name": "Duplicate",
  "email": "existing@example.com",
  "password": "Password123"
}

Response:
```
409 Conflict
{
  "error": "Conflict",
  "message": "User already exists with email: existing@example.com"
}
```

---

## cURL Commands

### Register User
```bash
curl -X POST http://localhost:8181/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Doe",
    "email": "john@example.com",
    "password": "SecurePassword123"
  }'
```

### Login User
```bash
curl -X POST http://localhost:8181/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "john@example.com",
    "password": "SecurePassword123"
  }'
```

### Get All Users (Replace TOKEN with actual token)
```bash
curl -X GET http://localhost:8181/api/users \
  -H "Authorization: Bearer TOKEN"
```

### Create Student
```bash
curl -X POST http://localhost:8181/students \
  -H "Authorization: Bearer TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "john",
    "lastName": "Doe",
    "age": 20
  }'
```

### Search Students
```bash
curl -X GET "http://localhost:8181/students/search?firstName=john" \
  -H "Authorization: Bearer TOKEN"
```

---

## Bash Script for Testing

```bash
#!/bin/bash

BASE_URL="http://localhost:8181"

# 1. Register user
echo "1. Registering user..."
RESPONSE=$(curl -s -X POST $BASE_URL/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test User",
    "email": "test@example.com",
    "password": "TestPassword123"
  }')

echo "Register Response: $RESPONSE"

# Extract token (using jq if available, otherwise manual parsing)
TOKEN=$(echo $RESPONSE | grep -o '"token":"[^"]*' | cut -d'"' -f4)
echo "Token: $TOKEN"

# 2. Get all users
echo -e "\n2. Getting all users..."
curl -s -X GET $BASE_URL/api/users \
  -H "Authorization: Bearer $TOKEN" | jq

# 3. Try admin endpoint (should fail)
echo -e "\n3. Trying admin endpoint (should fail)..."
curl -s -X GET $BASE_URL/api/users/admin/statistics \
  -H "Authorization: Bearer $TOKEN" | jq

# 4. Create student
echo -e "\n4. Creating student..."
curl -s -X POST $BASE_URL/students \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "John",
    "lastName": "Test",
    "age": 20
  }' | jq

# 5. Search students
echo -e "\n5. Searching students..."
curl -s -X GET "$BASE_URL/students/search?firstName=John" \
  -H "Authorization: Bearer $TOKEN" | jq
```

Save as `test_api.sh` and run:
```bash
chmod +x test_api.sh
./test_api.sh
```

---

## PowerShell Script for Testing

```powershell
$BaseUrl = "http://localhost:8181"

# 1. Register user
Write-Host "1. Registering user..." -ForegroundColor Green
$registerBody = @{
    name = "Test User"
    email = "test@example.com"
    password = "TestPassword123"
} | ConvertTo-Json

$registerResponse = Invoke-RestMethod -Uri "$BaseUrl/api/auth/register" `
  -Method POST `
  -ContentType "application/json" `
  -Body $registerBody

Write-Host "Register Response: $($registerResponse | ConvertTo-Json)"

$token = $registerResponse.token
Write-Host "Token: $token" -ForegroundColor Yellow

# 2. Get all users
Write-Host "`n2. Getting all users..." -ForegroundColor Green
$headers = @{
    Authorization = "Bearer $token"
}

$usersResponse = Invoke-RestMethod -Uri "$BaseUrl/api/users" `
  -Method GET `
  -Headers $headers

Write-Host "Users: $($usersResponse | ConvertTo-Json)"

# 3. Create student
Write-Host "`n3. Creating student..." -ForegroundColor Green
$studentBody = @{
    firstName = "John"
    lastName = "Test"
    age = 20
} | ConvertTo-Json

$studentResponse = Invoke-RestMethod -Uri "$BaseUrl/students" `
  -Method POST `
  -ContentType "application/json" `
  -Headers $headers `
  -Body $studentBody

Write-Host "Student Created: $($studentResponse | ConvertTo-Json)"

# 4. Search students
Write-Host "`n4. Searching students..." -ForegroundColor Green
$searchResponse = Invoke-RestMethod -Uri "$BaseUrl/students/search?firstName=John" `
  -Method GET `
  -Headers $headers

Write-Host "Search Results: $($searchResponse | ConvertTo-Json)"
```

Save as `test_api.ps1` and run:
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
.\test_api.ps1
```

---

## Expected Responses

### Successful Register
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJqb2huQGV4YW1wbGUuY29tIiwiYXV0aG9yaXRpZXMiOlsiUk9MRV9VU0VSIiwidXNlcjpyZWFkIl0sImlhdCI6MTcwNDA2NzIwMCwiZXhwIjoxNzA0MDcwODAwfQ.signature",
  "email": "john@example.com",
  "roles": [
    "ROLE_USER"
  ]
}
```

### Successful Login
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "email": "john@example.com",
  "roles": [
    "ROLE_USER"
  ]
}
```

### Get All Users
```json
[
  {
    "id": 1,
    "name": "John Doe",
    "email": "john@example.com",
    "enabled": true,
    "roles": [
      {
        "id": 1,
        "name": "ROLE_USER",
        "permissions": [
          {
            "id": 1,
            "name": "user:read"
          }
        ]
      }
    ]
  }
]
```

### 403 Forbidden Response
```json
{
  "timestamp": "2024-01-01T12:00:00.000+00:00",
  "status": 403,
  "error": "Forbidden",
  "message": "Access Denied: Principal does not have the required role: ROLE_ADMIN",
  "path": "/api/users/admin/statistics"
}
```

---

This comprehensive guide covers all possible testing scenarios!
