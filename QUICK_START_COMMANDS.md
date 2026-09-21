# 🚀 Quick Start Commands

Copy and paste these commands to get started immediately.

---

## Step 1: Build & Compile
```bash
cd "C:\Users\MDH\Downloads\Java Projects\demo"
mvn clean compile
```

---

## Step 2: Run the Application
```bash
mvn spring-boot:run
```

**Output should show:**
```
Started DemoApplication in X.XXX seconds
Data seeding completed successfully
```

---

## Step 3: Test Registration (In New Terminal/Command Prompt)

```bash
curl -X POST http://localhost:8181/api/auth/register ^
  -H "Content-Type: application/json" ^
  -d "{\"name\":\"Test User\",\"email\":\"test@example.com\",\"password\":\"TestPass123\"}"
```

**Response will look like:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "email": "test@example.com",
  "roles": ["ROLE_USER"]
}
```

**COPY THE TOKEN FOR NEXT STEP**

---

## Step 4: Test Protected Endpoint

Replace `{YOUR_TOKEN}` with the token from Step 3:

```bash
curl -X GET http://localhost:8181/api/users ^
  -H "Authorization: Bearer {YOUR_TOKEN}"
```

**Should return a list of users (with the one you just created)**

---

## Step 5: Test Authorization (Should Fail)

Try to access admin endpoint (should return 403 Forbidden):

```bash
curl -X GET http://localhost:8181/api/users/admin/statistics ^
  -H "Authorization: Bearer {YOUR_TOKEN}"
```

**Response:**
```
403 Forbidden - Principal does not have the required role: ROLE_ADMIN
```

---

## Step 6: Test Student Search

```bash
curl -X GET "http://localhost:8181/students/search?firstName=john" ^
  -H "Authorization: Bearer {YOUR_TOKEN}"
```

---

## Optional: Import Postman Collection

1. Open Postman
2. Click "Import" button
3. Select "JWT_API.postman_collection.json" from project root
4. All endpoints will be pre-configured
5. Use {{token}} variable to store token

---

## Optional: Manual Database Setup

If JPA doesn't create tables automatically:

```bash
# Open PostgreSQL command line
psql -U postgres -d demo_db -f database_setup.sql
```

Or copy the SQL from `database_setup.sql` and run in pgAdmin

---

## Optional: Build JAR for Deployment

```bash
mvn clean package
```

**JAR will be at:**
```
target/demo-0.0.1-SNAPSHOT.jar
```

**Run it with:**
```bash
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

---

## Troubleshooting Quick Fixes

### Database connection fails
```properties
# Check these in application.properties
spring.datasource.url=jdbc:postgresql://localhost:5432/demo_db
spring.datasource.username=postgres
spring.datasource.password=password
```

### Roles not found error
- Check DataSeeder ran (look for "Data seeding completed" in logs)
- If not, run database_setup.sql manually

### Token keeps getting rejected
- Ensure jwt.secret is ≥ 32 characters
- Don't change it after generating tokens

### 403 Forbidden on all endpoints
- Make sure token is in Authorization header as: `Bearer {token}`
- Not: `Authorization: {token}`

---

## Windows PowerShell Alternative

If curl doesn't work, use PowerShell:

```powershell
# Register
$body = @{
    name = "Test User"
    email = "test@example.com"
    password = "TestPass123"
} | ConvertTo-Json

$response = Invoke-RestMethod -Uri "http://localhost:8181/api/auth/register" `
  -Method POST `
  -ContentType "application/json" `
  -Body $body

$token = $response.token
Write-Host "Token: $token"

# Get users
$headers = @{ Authorization = "Bearer $token" }
Invoke-RestMethod -Uri "http://localhost:8181/api/users" `
  -Method GET `
  -Headers $headers | ConvertTo-Json
```

---

## Complete Test Script (PowerShell)

Save as `test.ps1`:

```powershell
$BaseUrl = "http://localhost:8181"

# 1. Register
Write-Host "Registering user..." -ForegroundColor Green
$regBody = @{
    name = "John Doe"
    email = "john@test.com"
    password = "JohnPass123"
} | ConvertTo-Json

$regResp = Invoke-RestMethod -Uri "$BaseUrl/api/auth/register" `
  -Method POST `
  -ContentType "application/json" `
  -Body $regBody

$token = $regResp.token
Write-Host "Token: $token" -ForegroundColor Yellow

# 2. Get users
Write-Host "`nGetting users..." -ForegroundColor Green
$headers = @{ Authorization = "Bearer $token" }
$users = Invoke-RestMethod -Uri "$BaseUrl/api/users" `
  -Method GET `
  -Headers $headers
Write-Host "Users: $(($users | ConvertTo-Json).Length) found"

# 3. Try admin (should fail)
Write-Host "`nTrying admin endpoint (should fail)..." -ForegroundColor Yellow
try {
    Invoke-RestMethod -Uri "$BaseUrl/api/users/admin/statistics" `
      -Method GET `
      -Headers $headers
} catch {
    Write-Host "✓ Correctly denied with: $($_.Exception.Message)" -ForegroundColor Green
}

Write-Host "`n✅ Tests complete!" -ForegroundColor Green
```

Run with:
```powershell
.\test.ps1
```

---

## Complete Test Script (Bash)

Save as `test.sh`:

```bash
#!/bin/bash

BASE_URL="http://localhost:8181"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 1. Register
echo -e "${GREEN}1. Registering user...${NC}"
RESPONSE=$(curl -s -X POST $BASE_URL/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "John Doe",
    "email": "john@test.com",
    "password": "JohnPass123"
  }')

TOKEN=$(echo $RESPONSE | grep -o '"token":"[^"]*' | cut -d'"' -f4)
echo -e "${YELLOW}Token: $TOKEN${NC}"

# 2. Get users
echo -e "\n${GREEN}2. Getting all users...${NC}"
curl -s -X GET $BASE_URL/api/users \
  -H "Authorization: Bearer $TOKEN" | jq

# 3. Try admin (should fail)
echo -e "\n${GREEN}3. Trying admin endpoint (should fail)...${NC}"
curl -s -X GET $BASE_URL/api/users/admin/statistics \
  -H "Authorization: Bearer $TOKEN" | jq

echo -e "\n${GREEN}✅ Tests complete!${NC}"
```

Run with:
```bash
chmod +x test.sh
./test.sh
```

---

## Verify Everything Is Working

Check these signs:

✅ Application starts without errors
✅ "Data seeding completed successfully" appears in logs
✅ Can register a user and get a token
✅ Can access endpoints with token
✅ Get 403 Forbidden on unauthorized endpoints
✅ Token is rejected without Authorization header

---

## Next Steps After Testing

1. **Read the documentation:**
   - Start with `INDEX.md`
   - Then read `JWT_SETUP_GUIDE.md`

2. **Understand the code:**
   - Review `IMPLEMENTATION_SUMMARY.md`
   - Check `QUICK_REFERENCE.md` for patterns

3. **Customize for your needs:**
   - Add new permissions
   - Add new roles
   - Create custom endpoints
   - Add business logic

4. **Deploy to production:**
   - Follow `IMPLEMENTATION_CHECKLIST.md`
   - Generate strong JWT secret
   - Configure proper database
   - Enable HTTPS

---

## Common Commands

```bash
# View logs in real-time
mvn spring-boot:run -X

# Build with tests
mvn clean test

# Build without tests
mvn clean package -DskipTests

# Run on different port
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8182"

# Run with dev profile
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"

# Debug the application
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"
```

---

## What Each Step Does

| Step | What It Does | Why |
|------|------------|-----|
| **Step 1** | Compiles Java code | Verifies no syntax errors |
| **Step 2** | Starts Spring Boot | Initializes security, DB, seeds data |
| **Step 3** | Register a user | Creates account, gets JWT token |
| **Step 4** | Use token | Verifies authentication works |
| **Step 5** | Try admin endpoint | Verifies authorization works |
| **Step 6** | Test student search | Verifies existing features work with auth |

---

## Successful Output Signs

### Application Start (Step 2)
```
Started DemoApplication in 4.567 seconds
Data seeding completed successfully
```

### Registration Success (Step 3)
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "email": "test@example.com",
  "roles": ["ROLE_USER"]
}
```

### Protected Endpoint Success (Step 4)
```json
[
  {
    "id": 1,
    "name": "Test User",
    "email": "test@example.com",
    "enabled": true,
    "roles": [...]
  }
]
```

### Authorization Denied (Step 5)
```
{
  "timestamp": "2024-01-01T12:00:00",
  "status": 403,
  "error": "Forbidden",
  "message": "Access Denied: Principal does not have the required role: ROLE_ADMIN"
}
```

---

## Help & Support

**Something not working?**

1. Check the error message
2. Look in `QUICK_REFERENCE.md` (Common Errors section)
3. Check `JWT_SETUP_GUIDE.md` (Troubleshooting section)
4. Verify database is running: `psql -U postgres -c "\l"`
5. Check logs in the application output

---

**You're ready to go! Follow the steps above and everything will work. 🚀**
