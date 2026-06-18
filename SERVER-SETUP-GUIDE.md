# MCDG Server Setup Guide

**Status:** Production Ready  
**Created:** 2026-06-17  
**Goal:** Complete guide for deploying MCDG mod on a production Minecraft server

---

## Server Requirements

### **Hardware Requirements**

**Minimum (2-4 players):**
- CPU: 2 cores, 2.4 GHz+
- RAM: 4 GB allocated to server
- Storage: 10 GB SSD
- Network: 10 Mbps upload

**Recommended (5-10 players):**
- CPU: 4 cores, 3.0 GHz+
- RAM: 8 GB allocated to server
- Storage: 20 GB SSD
- Network: 25 Mbps upload

**High Performance (10+ players):**
- CPU: 6+ cores, 3.5 GHz+
- RAM: 16 GB allocated to server
- Storage: 50 GB SSD (NVMe preferred)
- Network: 50+ Mbps upload

### **Software Requirements**

**Mandatory:**
- Java 21 (JDK or JRE)
- Minecraft Server 1.20.6
- Fabric Loader 0.16.10
- MCDG Mod (latest stable release)

**Optional but Recommended:**
- SSH access for remote management
- Screen/tmux for session management
- Backup solution (automated backups)
- Monitoring tools (server performance, player activity)

---

## Installation Steps

### **Step 1: Prepare Server Environment**

#### **1.1 Install Java 21**
```bash
# Ubuntu/Debian
sudo apt update
sudo apt install openjdk-21-jre-headless

# CentOS/RHEL
sudo yum install java-21-openjdk-headless

# Verify installation
java -version
```

#### **1.2 Create Server Directory**
```bash
mkdir -p /opt/mcdg-server
cd /opt/mcdg-server
```

#### **1.3 Download Minecraft Server**
```bash
# Download Minecraft 1.20.6 server jar
wget https://launcher.mojang.com/v1/objects/8f3112a1049751c472beb9697ae9425ac0d371a9/server.jar

# Accept EULA
echo "eula=true" > eula.txt
```

#### **1.4 Install Fabric Loader**
```bash
# Download Fabric installer
wget https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.1/fabric-installer-1.0.1.jar

# Install server
java -jar fabric-installer-1.0.1.jar server -mcversion 1.20.6 -loader 0.16.10

# This creates:
# - fabric-server-launcher.jar (main server jar)
# - .fabric/ (Fabric configuration)
# - server.properties (default Minecraft config)
```

### **Step 2: Install MCDG Mod**

#### **2.1 Obtain MCDG Mod**
```bash
# Option 1: Build from source
git clone https://github.com/rbrambley/mcdg.git
cd mcdg
./gradlew build
# Copy: build/libs/mcdg-0.1.0.jar to server mods folder

# Option 2: Download release (when available)
# Download from GitHub releases or mod distribution
```

#### **2.2 Install Mod Files**
```bash
# Create mods directory
mkdir -p /opt/mcdg-server/mods

# Copy MCDG mod
cp mcdg-0.1.0.jar /opt/mcdg-server/mods/

# Copy any required dependencies (Fabric API is usually included)
```

### **Step 3: Configure Server**

#### **3.1 Basic Server Configuration**
Edit `server.properties`:
```properties
# Basic Settings
server-name=MCDG Disc Golf Server
motd=Welcome to MCDG Disc Golf Server!
max-players=20
difficulty=peaceful
gamemode=survival
hardcore=false

# World Settings
level-name=world
level-seed=
level-type=default
generate-structures=true

# Network Settings
server-port=25565
server-ip=
pvp=false
enable-query=false
enable-rcon=false

# Performance Settings
view-distance=10
simulation-distance=10
max-tick-time=60000
max-world-size=29999984

# Player Settings
white-list=false
enforce-whitelist=false
spawn-protection=0
spawn-monsters=false
spawn-animals=true
spawn-npcs=true
```

#### **3.2 MCDG-Specific Configuration**
Create `config/mcdg-server.properties` (if using custom config):
```properties
# MCDG Server Configuration
# Enable/disable features
enableHudScoringDebug=false
enableStrictFlowDebug=false
skipRoundPresentation=false
respawnPenaltyStrokes=1
defaultHoleCount=9
enforceCourseProtection=true
enableSurvivalRewards=true

# Performance settings
enableWindSystem=true
defaultWindSpeed=0.2
windUpdateInterval=200
```

#### **3.3 Performance Optimization**
Add to server startup flags:
```bash
java -Xms4G -Xmx8G \
  -XX:+UseG1GC \
  -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+DisableExplicitGC \
  -XX:+AlwaysPreTouch \
  -XX:G1NewSizePercent=30 \
  -XX:G1MaxNewSizePercent=40 \
  -XX:G1HeapRegionSize=8M \
  -XX:G1ReservePercent=20 \
  -XX:G1HeapWastePercent=5 \
  -XX:G1MixedGCCountTarget=4 \
  -XX:InitiatingHeapOccupancyPercent=15 \
  -XX:G1MixedGCLiveThresholdPercent=90 \
  -XX:G1RSetUpdatingPauseTimePercent=5 \
  -XX:SurvivorRatio=32 \
  -XX:+PerfDisableSharedMem \
  -XX:MaxTenuringThreshold=1 \
  -Dusing.aikars.flags=https://mcflags.emc.gs \
  -Daikars.new.flags=true \
  -jar fabric-server-launcher.jar nogui
```

### **Step 4: Network Configuration**

#### **4.1 Firewall Setup**
```bash
# Ubuntu/Debian (ufw)
sudo ufw allow 25565/tcp
sudo ufw reload

# CentOS/RHEL (firewalld)
sudo firewall-cmd --permanent --add-port=25565/tcp
sudo firewall-cmd --reload
```

#### **4.2 Port Forwarding (if hosting from home)
- Forward external port 25565 to internal server IP
- Configure router with static IP for server
- Consider using DDNS for dynamic IP

#### **4.3 Domain Setup (Optional)
- Purchase domain name
- Configure DNS A record to server IP
- Set up SRV record for Minecraft (optional)
  - Service: _minecraft
  - Protocol: _tcp
  - Port: 25565
  - Target: your domain

### **Step 5: Server Management**

#### **5.1 Start Server with Screen**
```bash
# Install screen
sudo apt install screen  # Ubuntu/Debian
sudo yum install screen  # CentOS/RHEL

# Create screen session
screen -S mcdg-server

# Start server
cd /opt/mcdg-server
java -Xms4G -Xmx8G -jar fabric-server-launcher.jar nogui

# Detach from screen: Ctrl+A, then D
# Reattach to screen: screen -r mcdg-server
```

#### **5.2 Create Startup Script**
Create `start-server.sh`:
```bash
#!/bin/bash
cd /opt/mcdg-server
screen -dmS mcdg-server java -Xms4G -Xmx8G -jar fabric-server-launcher.jar nogui
echo "MCDG server started in screen session 'mcdg-server'"
```

Make executable:
```bash
chmod +x start-server.sh
```

#### **5.3 Create Stop Script**
Create `stop-server.sh`:
```bash
#!/bin/bash
screen -S mcdg-server -X stuff "stop\n"
echo "Stop command sent to MCDG server"
```

Make executable:
```bash
chmod +x stop-server.sh
```

---

## Client Setup

### **Client Requirements**
- Minecraft 1.20.6
- Fabric Loader 0.16.10
- MCDG Mod (same version as server)
- Java 21

### **Client Installation Steps**

#### **Option 1: Using Fabric Installer (Recommended)**
1. Download Fabric installer from https://fabricmc.net/
2. Run installer: Select "Client" installation
3. Select Minecraft 1.20.6 and Loader 0.16.10
4. Create new profile in Minecraft Launcher
5. Install MCDG mod:
   - Navigate to `.minecraft/mods/`
   - Copy `mcdg-0.1.0.jar` to mods folder
   - Launch Minecraft with Fabric profile

#### **Option 2: Using MultiMC/Prism Launcher**
1. Create new instance
2. Select Minecraft 1.20.6
3. Add Fabric Loader component
4. Add MCDG mod as external mod
5. Launch instance

#### **Option 3: ATLauncher (Current Development Setup)**
1. Create new instance
2. Select Minecraft 1.20.6 with Fabric
3. Add MCDG mod to mods folder
4. Launch instance

### **Client Configuration**
No client-side configuration required for basic multiplayer. MCDG handles client-server synchronization automatically.

---

## Server Administration

### **Basic Admin Commands**

**MCDG-Specific Commands:**
```
/mcdg                    # Main menu
/mcdg help              # Show all commands
/mcdg listcourses       # List available courses
/mcdg usecourse <index> # Select course from catalog
/mcdg startround        # Start a round
/mcdg endround          # End current round
/mcdg buildresort       # Build resort at current location
/mcdg autocourse        # Generate automatic course
```

**Standard Minecraft Commands:**
```
/op <player>            # Grant operator status
/deop <player>          # Remove operator status
/gamemode <mode>        # Change game mode
/time set <time>        # Set time of day
/weather <type>         # Set weather
```

### **Player Management**

**Whitelist Management:**
```
/whitelist on           # Enable whitelist
/whitelist off          # Disable whitelist
/whitelist add <player> # Add player to whitelist
/whitelist remove <player> # Remove player from whitelist
/whitelist list         # List all whitelisted players
```

**Ban Management:**
```
/ban <player>           # Ban player
/pardon <player>        # Unban player
/ban-ip <ip>            # Ban IP address
/pardon-ip <ip>         # Unban IP address
```

### **World Management**

**World Backup:**
```bash
# Stop server first
./stop-server.sh

# Create backup
cd /opt/mcdg-server
tar -czf ../mcdg-backup-$(date +%Y%m%d).tar.gz world/

# Restart server
./start-server.sh
```

**Automated Backups:**
Create cron job:
```bash
# Edit crontab
crontab -e

# Add daily backup at 3 AM
0 3 * * * cd /opt/mcdg-server && ./stop-server.sh && tar -czf ../mcdg-backup-$(date +\%Y\%m\%d).tar.gz world/ && ./start-server.sh
```

**World Reset (if needed):**
```bash
# Stop server
./stop-server.sh

# Backup current world
tar -czf ../mcdg-backup-before-reset.tar.gz world/

# Delete world
rm -rf world/

# Start server (will generate new world)
./start-server.sh
```

---

## Monitoring and Maintenance

### **Server Monitoring**

**Resource Monitoring:**
```bash
# CPU and memory
htop

# Disk usage
df -h

# Network connections
netstat -tunp | grep 25565
```

**Log Monitoring:**
```bash
# Server logs
tail -f logs/latest.log

# Check for errors
grep -i error logs/latest.log

# Check for warnings
grep -i warn logs/latest.log
```

### **Performance Tuning**

**If server is lagging:**
1. Reduce view-distance in server.properties
2. Reduce allocated RAM if over-provisioned
3. Check for excessive entity counts
4. Consider upgrading hardware
5. Review MCDG performance settings

**Common Performance Issues:**
- **Course placement lag:** Use tick-incremental placement (already implemented)
- **Minimap rendering:** Client-side only, doesn't affect server
- **Multiple players:** Ensure sufficient RAM and CPU

### **Updates and Maintenance**

**Updating MCDG Mod:**
```bash
# Stop server
./stop-server.sh

# Backup current version
cp mods/mcdg-0.1.0.jar mods/mcdg-0.1.0.jar.backup

# Replace with new version
cp mcdg-0.2.0.jar mods/

# Start server
./start-server.sh

# Test thoroughly before allowing players to join
```

**Updating Minecraft/Fabric:**
```bash
# Stop server
./stop-server.sh

# Backup entire server directory
cp -r /opt/mcdg-server /opt/mcdg-server-backup

# Update Fabric installer
java -jar fabric-installer-1.0.1.jar server -mcversion 1.20.6 -loader 0.16.10

# Start server
./start-server.sh
```

---

## Security Considerations

### **Basic Security**
- Use strong passwords for server access
- Keep Java and system packages updated
- Use firewall to restrict access
- Consider using a VPN for admin access
- Regular security updates

### **Player Safety**
- Enable whitelist for private servers
- Monitor chat for inappropriate behavior
- Have clear server rules
- Consider chat moderation plugins
- Regular backups to prevent griefing

### **Data Protection**
- Regular automated backups
- Offsite backup storage
- Secure backup access
- Test backup restoration

---

## Troubleshooting

### **Common Issues**

**Server won't start:**
1. Check Java version: `java -version` (must be 21)
2. Check fabric-server-launcher.jar exists
3. Check logs/latest.log for error messages
4. Verify sufficient RAM allocation

**Players can't connect:**
1. Check server is running: `netstat -tunp | grep 25565`
2. Check firewall allows port 25565
3. Verify correct server IP and port
4. Check if whitelist is enabled
5. Verify client has correct mod version

**Mod conflicts:**
1. Check logs/latest.log for mod errors
2. Try running with only MCDG and Fabric API
3. Check for other mods that modify similar mechanics
4. Ensure all players have exact same mod versions

**Performance issues:**
1. Check server resource usage: `htop`
2. Reduce view-distance in server.properties
3. Check for excessive entities or course generation
4. Consider hardware upgrade
5. Review MCDG performance settings

**World generation issues:**
1. Check level-seed in server.properties
2. Verify sufficient disk space
3. Check for corruption in world files
4. Consider regenerating world if severely corrupted

---

## Advanced Configuration

### **Multi-World Setup**
Create separate worlds for different purposes:
- Main world: General play
- Course world: Dedicated course building
- Resort world: Resort structure

Configure in server.properties or use multi-world plugins.

### **Database Integration**
For advanced statistics and leaderboards:
- Set up MySQL/MariaDB database
- Configure MCDG to use database (if implemented)
- Regular database backups
- Consider database replication for high availability

### **Load Balancing**
For very large servers:
- Use proxy server (Velocity/BungeeCord)
- Distribute players across multiple backend servers
- Shared database for player data
- Centralized authentication

---

## Resource Pack Distribution

### **Server Resource Pack**
Create server resource pack for custom textures:
1. Create resource pack with MCDG-specific textures
2. Host on web server or file sharing
3. Configure in server.properties:
```properties
resource-pack=https://your-server.com/mcdg-resource-pack.zip
resource-pack-sha1=<sha1-hash-of-zip>
require-resource-pack=true
```

### **Automatic Distribution**
Players will be prompted to download resource pack when joining server.

---

## Backup and Disaster Recovery

### **Backup Strategy**
**Daily Backups:**
- World files
- Server configuration
- Player data
- Course catalog

**Weekly Backups:**
- Complete server directory
- Mod versions
- Configuration files

**Offsite Storage:**
- Cloud storage (AWS S3, Google Drive, etc.)
- Remote server
- Physical media (external drives)

### **Disaster Recovery Plan**
1. Assess damage and determine recovery scope
2. Restore from most recent good backup
3. Test restored server functionality
4. Communicate with players about downtime
5. Implement preventive measures for future

---

## Community Management

### **Server Rules**
Create clear server rules:
- No griefing or cheating
- Respect other players
- Follow chat guidelines
- Report bugs to server admin
- Have fun!

### **Communication**
- Discord server for community
- Regular updates and announcements
- Bug reporting system
- Feature request process
- Community events and tournaments

### **Support**
- Provide help for new players
- Create tutorials and guides
- Have active moderators/admins
- Regular server maintenance schedule
- Clear communication about downtime

---

## Cost Considerations

### **Hosting Options**

**Self-Hosted:**
- Hardware: $200-1000 one-time
- Electricity: $20-50/month
- Internet: $30-100/month
- Total: $50-150/month

**VPS Hosting:**
- Basic VPS: $5-20/month
- Performance VPS: $20-50/month
- Dedicated server: $50-200/month

**Minecraft Hosting Services:**
- Shared hosting: $5-15/month
- VPS hosting: $15-50/month
- Dedicated hosting: $50-100/month

### **Recommended Setup**
**Small Server (2-4 players):**
- Basic VPS ($10-20/month)
- 4GB RAM
- Standard CPU

**Medium Server (5-10 players):**
- Performance VPS ($30-50/month)
- 8GB RAM
- Multi-core CPU

**Large Server (10+ players):**
- Dedicated server ($50-100/month)
- 16GB+ RAM
- High-performance CPU

---

## Legal and Licensing

### **Minecraft EULA**
- Follow Minecraft EULA
- No monetization that violates EULA
- Proper attribution for mods
- Respect intellectual property

### **MCDG Licensing**
- Check MCDG license for usage terms
- Attribute properly if redistributing
- Follow modification guidelines
- Respect copyright and trademarks

---

## Next Steps

1. **Assess your needs** - Determine expected player count and budget
2. **Choose hosting** - Decide between self-hosted or paid hosting
3. **Follow installation guide** - Complete server setup
4. **Test thoroughly** - Verify all functionality before public launch
5. **Set up monitoring** - Implement monitoring and backups
6. **Launch community** - Invite players and build community
7. **Maintain regularly** - Keep server updated and secure

---

## Additional Resources

**MCDG-Specific:**
- GitHub Repository: https://github.com/rbrambley/mcdg
- Issue Tracker: https://github.com/rbrambley/mcdg/issues
- Documentation: Various .md files in repository

**Minecraft/Fabric:**
- Fabric Wiki: https://fabricmc.net/wiki/
- Minecraft Wiki: https://minecraft.fandom.com/
- Server Administration: Various online guides

**Community:**
- Minecraft Forums: https://www.minecraftforum.net/
- r/Minecraft: https://reddit.com/r/Minecraft
- Fabric Discord: https://discord.gg/v6v4pMv

---

## Support

For MCDG-specific issues:
- Check GitHub issues first
- Review existing documentation
- Report bugs with detailed information
- Include server logs and configuration

For general Minecraft server issues:
- Minecraft Forums
- Server administration communities
- Fabric Discord support channels
