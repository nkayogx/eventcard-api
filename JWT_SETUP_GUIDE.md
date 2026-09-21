## JWT Authentication & Authorization System - Setup & Usage Guide

### Overview
This is a complete JWT-based authentication and authorization system for your Spring Boot REST API. It includes:
- Role-based access control (RBAC)
- Permission-based access control (PBAC)
- JWT token generation and validation
- Stateless session management
- Method-level security with @PreAuthorize annotations
- Data seeding on startup

---

## 🚀 Quick Start

### 1. Database Setup
Ensure PostgreSQL is running on `localhost:5432` with:
- Database: `demo_db`
- Username: `postgres`
- Password: `password`

Update these in `application.properties` if different.

### 2. Build & Run
```bash
mvn clean install
mvn spring-boot:run
```

The application will:
- Start on `http://localhost:8181`
- Auto-create tables via JPA
- Seed permissions and roles on startup

---

## 📋 API Endpoints

### Authentication (Public - No Auth Required)

#### 1. Register New User
```http
POST /api/auth/register
Content-Type: application/json

{
  "name": "John Doe",
  "email": "john@example.com",
  "password": "SecurePassword123"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "email": "john@example.com",
  "roles": ["ROLE_USER"]
}
```

#### 2. Login
```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "john@example.com",
  "password": "SecurePassword123"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "email": "john@example.com",
  "roles": ["ROLE_USER"]
}
```

---

### User Management (Protected - Requires JWT Token)

#### 3. Get All Users (Requires: `user:read` permission)
```http
GET /api/users
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

#### 4. Get User by ID (Requires: `user:read` permission)
```http
GET /api/users/{id}
Authorization: Bearer {token}
```

#### 5. Create User (Requires: `user:create` permission)
```http
POST /api/users
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "Jane Doe",
  "email": "jane@example.com",
  "password": "SecurePassword456"
}
```

#### 6. Update User (Requires: `admin:all` OR (`user:read` AND `user:delete`))
```http
PUT /api/users/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "Jane Smith",
  "email": "jane.smith@example.com"
}
```

#### 7. Delete User (Requires: `user:delete` permission OR `ROLE_ADMIN`)
```http
DELETE /api/users/{id}
Authorization: Bearer {token}
```

#### 8. Admin Statistics (Requires: `ROLE_ADMIN`)
```http
GET /api/users/admin/statistics
Authorization: Bearer {token}
```

---

### Student Management (Protected - Requires JWT Token)

#### 9. Get All Students (Requires: `user:read` permission)
```http
GET /students
Authorization: Bearer {token}
```

#### 10. Get Student by ID (Requires: `user:read` permission)
```http
GET /students/{student_id}
Authorization: Bearer {token}
```

#### 11. Create Student (Requires: `user:create` permission)
```http
POST /students
Authorization: Bearer {token}
Content-Type: application/json

{
  "firstName": "john",
  "lastName": "Doe",
  "age": 20
}
```

#### 12. Search Students by First Name (Requires: `user:read` permission)
```http
GET /students/search?firstName=john
Authorization: Bearer {token}
```

---

## 🔐 Security Model

### Default Roles & Permissions (Auto-Created)

**ROLE_USER:**
- ✅ `user:read` - Can read user data
- ❌ Cannot create, delete, or perform admin actions

**ROLE_ADMIN:**
- ✅ `user:read` - Read user data
- ✅ `user:create` - Create new users
- ✅ `user:delete` - Delete users
- ✅ `admin:all` - All admin operations

### Permission Codes
- `user:read` - Read user information
- `user:create` - Create new users
- `user:delete` - Delete users
- `admin:all` - All administrative operations

---

## 📝 How the System Works

### 1. **User Registration**
- User provides: name, email, password
- Password is encoded with BCrypt
- User automatically assigned `ROLE_USER`
- JWT token is generated and returned
- User can immediately use the token

### 2. **User Login**
- User provides: email, password
- AuthenticationManager validates credentials
- If valid, JWT token is generated
- Token contains: username, authorities (roles + permissions), expiration

### 3. **JWT Token Structure**
The JWT contains:
```json
{
  "sub": "john@example.com",
  "authorities": [
    "ROLE_USER",
    "user:read"
  ],
  "iat": 1704067200,
  "exp": 1704070800
}
```

### 4. **Request Authorization Flow**
1. Client sends request with `Authorization: Bearer {token}` header
2. `JwtAuthFilter` intercepts the request
3. Filter extracts token from header
4. `JwtUtil.validateToken()` verifies signature and expiration
5. If valid, extract username and authorities
6. Set authentication in `SecurityContextHolder`
7. Controller method checks `@PreAuthorize` expression
8. If authorized, execute method; if not, return 403 Forbidden

### 5. **Method-Level Security**
Uses Spring Security's `@PreAuthorize` annotation:

```java
// User must have 'user:read' authority
@PreAuthorize("hasAuthority('user:read')")

// User must have 'ROLE_ADMIN' role
@PreAuthorize("hasRole('ROLE_ADMIN')")

// User must have EITHER permission
@PreAuthorize("hasAuthority('user:delete') or hasRole('ROLE_ADMIN')")

// User must have ALL permissions
@PreAuthorize("hasAuthority('user:read') and hasAuthority('user:delete')")
```

---

## 🔑 Key Classes & Files

### Security Layer
- **JwtUtil.java** - Generate and validate JWT tokens
- **JwtAuthFilter.java** - Intercepts requests and sets authentication
- **SecurityConfig.java** - Configures Spring Security

### Services
- **AuthService.java** - Handles register/login logic
- **CustomUserDetailsService.java** - Loads user details for authentication

### Controllers
- **AuthController.java** - `/api/auth/register`, `/api/auth/login`
- **UserManagementController.java** - User CRUD with method-level security
- **StudentController.java** - Student endpoints (already existed)

### Database
- **User.java** - User entity with UserDetails implementation
- **Role.java** - Role entity with ManyToMany Permission relationship
- **Permission.java** - Permission entity

### Configuration
- **DataSeeder.java** - Auto-creates roles and permissions on startup
- **application.properties** - JWT secret, expiration, database config

---

## 🧪 Testing with Postman

### Step 1: Register a New User
```bash
POST http://localhost:8181/api/auth/register
Body:
{
  "name": "Test User",
  "email": "test@example.com",
  "password": "password123"
}
```
Copy the token from the response.

### Step 2: Use Token to Access Protected Resource
```bash
GET http://localhost:8181/api/users
Headers:
Authorization: Bearer {token_from_step_1}
```

### Step 3: Try to Access Admin Endpoint (Should Fail)
```bash
GET http://localhost:8181/api/users/admin/statistics
Headers:
Authorization: Bearer {token_from_step_1}
```
Response: `403 Forbidden` - User only has `ROLE_USER`

### Step 4: Register Admin User
You would need to manually insert an admin user into the database or create an admin registration endpoint.

---

## 🐛 Troubleshooting

### Token is invalid or expired
- Check token hasn't expired (default 1 hour)
- Verify `Authorization` header format: `Bearer {token}`
- Ensure JWT secret in `application.properties` matches

### 403 Forbidden - Permission Denied
- Check user's roles and permissions in database
- Verify endpoint's `@PreAuthorize` expression
- Check authorities in JWT token payload

### Database connection fails
- Ensure PostgreSQL is running
- Check datasource URL, username, password in `application.properties`
- Verify `demo_db` database exists

### Roles not found error during registration
- Check DataSeeder ran successfully (check logs for "Data seeding completed")
- Ensure `ROLE_USER` exists in `roles` table

---

## 🔐 Security Best Practices

1. **JWT Secret**: Change `jwt.secret` to a strong, random value (min 32 chars)
   ```properties
   jwt.secret=your-super-secret-key-change-this-in-production
   ```

2. **Expiration**: Adjust `jwt.expiration` based on your needs
   ```properties
   jwt.expiration=3600000  # 1 hour in milliseconds
   ```

3. **HTTPS**: Always use HTTPS in production

4. **Token Refresh**: Consider implementing token refresh endpoints

5. **CORS**: Configure CORS for frontend integration

6. **Password Policy**: Implement strong password requirements

---

## ✅ Summary

This system provides:
- ✅ JWT token-based authentication
- ✅ Role-based access control (RBAC)
- ✅ Permission-based access control (PBAC)
- ✅ Stateless session management
- ✅ Method-level security
- ✅ Automatic data seeding
- ✅ BCrypt password encoding
- ✅ Request logging and debugging

All components are fully implemented and ready to use!
