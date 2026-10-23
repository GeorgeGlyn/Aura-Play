# Aura Play for Samsung Tizen TV & Web

A unified 4K Cinema & TV streaming app designed for **Samsung Smart TV (Tizen OS)** with remote control D-Pad navigation, as well as **Mobile & Desktop** browsers.

👉 **Live Web App on GitHub Pages**: [https://georgeglyn.github.io/Aura-Play/](https://georgeglyn.github.io/Aura-Play/)

---

## 📺 How to Install on Samsung Smart TV (Tizen OS)

### Step 1: Enable Developer Mode on Your Samsung TV
1. Turn on your Samsung TV and open the **Apps** panel from the Home menu.
2. On the remote control, press the numeric keys in sequence: **`1` `2` `3` `4` `5`**.
3. A **Developer Mode Configuration** popup will appear:
   * Toggle Developer Mode to **On**.
   * Enter your PC's local IP address (e.g., `192.168.1.50`) in the **Host PC IP** field.
4. Long-press the **Power** button on your remote until the TV restarts to apply changes.

---

### Step 2: Connect PC to TV via SDB
Ensure your PC and Samsung TV are on the **same Wi-Fi network**.
Find your TV IP address in: **Settings > General > Network > Network Status > IP Settings**.

From your computer's terminal:
```bash
# Connect to TV on port 26101
sdb connect <YOUR_TV_IP>:26101

# Verify the TV is listed
sdb devices
# Output: <YOUR_TV_IP>:26101   device   QE55Q...
```

---

### Step 3: Install AuraPlay.wgt onto the TV

#### Method A: Using Tizen CLI (Fastest)
Run the following in the `aura-play-web/` directory:
```bash
# 1. Build the .wgt package
npm run build:wgt

# 2. Install to the connected TV
tizen install -s <YOUR_TV_IP>:26101 -n AuraPlay.wgt
```

#### Method B: Using Samsung Tizen Studio (IDE)
1. Launch **Tizen Studio**.
2. **Workspace Prompt**: In the *Workspace Launcher* dialog, choose your regular workspace folder (e.g., `C:\Users\<username>\workspace` or any folder **outside** `d:\Aura-Play\aura-play-web`).
   > ⚠️ **Important**: Do **not** select `d:\Aura-Play\aura-play-web` as the Eclipse/Tizen workspace directory itself, because Eclipse requires projects to be imported into a workspace, not be the workspace itself.
3. In Tizen Studio, click: **File > Import...**
4. Expand **Tizen** and select **Tizen Project**, then click **Next**.
5. Select **Select root directory**, click **Browse...**, and navigate to:
   `d:\Aura-Play\aura-play-web`
6. You will see **`AuraPlay`** appear in the projects list with a checkmark.
7. Click **Finish**.
8. The **AuraPlay** project will now appear in your **Project Explorer**!
9. Right-click the project > **Run As > 1 Tizen Web Application** (or **Run As > 2 Tizen Web Application on TV Simulator**).

---

## 🎮 TV Remote Navigation Controls
* **D-Pad (Arrows)**: Move focus between cards, buttons, tabs, and carousels.
* **OK / Enter**: Select, open details, or start streaming.
* **Return / Back (`10009` or `Esc`)**: Close player, close modal, or go back to Home.
* **Play / Pause**: Toggle playback in the cinema player.
* **Rewind / Fast Forward**: Seek 10s backward or forward.

---

## 💻 Running / Testing in Browser (PC / Mobile)
You can run and test the app in any modern browser immediately:
```bash
cd aura-play-web
npx serve . -l 3000
```
Then visit `http://localhost:3000` on your PC, or `http://<YOUR_PC_IP>:3000` from your phone or TV web browser!
