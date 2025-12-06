# Koda Local Testing Guide

Test everything locally before deploying to DigitalOcean!

## Option 1: Use Existing Dev Environment (Recommended)

Koda already has a complete development environment in `docker/devenv/`:

```bash
cd docker/devenv
docker compose up -d
```

Then follow the main README for running backend/frontend.

## Option 2: Lightweight Local Services

Use this simpler setup if you just need databases for testing:

```bash
cd docker/local
docker compose up -d
```

This starts:
- **PostgreSQL** on port 5432
- **Redis** on port 6379
- **Mailhog** (email catcher) - Web UI at http://localhost:8025
- **MinIO** (S3 storage) - Console at http://localhost:9001

### 2. Setup Environment

Copy the environment file to project root:
```bash
cp docker/local/.env.example .env
```

### 3. Run Backend

```bash
cd backend
# Install dependencies and start REPL
./scripts/repl
```

Or with Clojure CLI:
```bash
clojure -M:dev
```

### 4. Run Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend will be at http://localhost:3449

### 5. Run Exporter (Optional)

```bash
cd exporter
npm install
npm run dev
```

---

## Testing Checklist

Before deploying to DigitalOcean, verify all these work:

### Authentication
- [ ] User registration works
- [ ] Email verification received (check http://localhost:8025)
- [ ] Login works
- [ ] Password reset works
- [ ] Logout works

### Projects
- [ ] Create new project
- [ ] Open existing project
- [ ] Rename project
- [ ] Delete project
- [ ] Duplicate project

### Design Tools
- [ ] Create shapes (rectangle, circle, etc.)
- [ ] Add text
- [ ] Import images
- [ ] Use pen tool
- [ ] Apply fills and strokes
- [ ] Use layers panel
- [ ] Group/ungroup objects
- [ ] Align and distribute

### Components
- [ ] Create component
- [ ] Use component instances
- [ ] Update main component (instances update)
- [ ] Detach instance

### Prototyping
- [ ] Create frames
- [ ] Add interactions
- [ ] Preview prototype
- [ ] Navigate between frames

### Code Generation (Koda-AI)
- [ ] Select component/frame
- [ ] Click "Generate Code" in menu
- [ ] Modal opens correctly
- [ ] Select framework (React, Vue, etc.)
- [ ] Generate code successfully
- [ ] Copy code works
- [ ] Download code works

### Export
- [ ] Export as PNG
- [ ] Export as SVG
- [ ] Export as PDF
- [ ] Export multiple assets

### Collaboration (if applicable)
- [ ] Share project link
- [ ] Real-time collaboration
- [ ] Comments

### File Storage
- [ ] Upload images (stored in MinIO)
- [ ] Images load correctly
- [ ] Check MinIO console for files

---

## Service URLs

| Service | URL | Purpose |
|---------|-----|---------|
| Frontend | http://localhost:3449 | Main application |
| Backend API | http://localhost:6060 | API server |
| Mailhog | http://localhost:8025 | View sent emails |
| MinIO Console | http://localhost:9001 | File storage admin |
| PostgreSQL | localhost:5432 | Database |
| Redis | localhost:6379 | Cache |

---

## Troubleshooting

### Database Issues
```bash
# Check if PostgreSQL is running
docker compose ps postgres

# View logs
docker compose logs postgres

# Connect to database
docker compose exec postgres psql -U koda -d koda
```

### Email Not Working
```bash
# Check Mailhog is running
docker compose ps mailhog

# All emails are caught at http://localhost:8025
```

### Storage Issues
```bash
# Check MinIO
docker compose logs minio

# Access console at http://localhost:9001
# Login: koda_access_key / koda_secret_key
```

### Reset Everything
```bash
# Stop all services and delete data
docker compose down -v

# Start fresh
docker compose up -d
```

---

## Performance Notes

Local testing is slower than production because:
- Hot reloading is enabled
- Debug logging is on
- No CDN/caching

Production will be faster!

---

## When You're Ready for Production

Once all tests pass:

1. Sign up for DigitalOcean (use student credits)
2. Follow `docker/production/DEPLOYMENT.md`
3. Your credits will last longer because you won't waste time debugging!
