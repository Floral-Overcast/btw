#!/bin/sh
# btw first-run setup, run once inside the freshly extracted rootfs under
# proot (fake root). Goal: zero terminal commands between Install and a
# working `pacman -Syu`. Best-effort - each step reports and we keep going
# so one failure doesn't block the rest. resolv.conf is written by the app
# from the host side before this runs.

echo "[btw] first-run setup starting"

# Hostname (cosmetic, keeps some tools from complaining).
echo btw > /etc/hostname 2>/dev/null

# Locale: generate en_US.UTF-8 and make it the default.
if ! grep -q "^en_US.UTF-8 UTF-8" /etc/locale.gen 2>/dev/null; then
  echo "en_US.UTF-8 UTF-8" >> /etc/locale.gen
fi
echo "[btw] generating locale"
locale-gen && echo "LANG=en_US.UTF-8" > /etc/locale.conf

# Pacman keyring: Arch Linux ARM ships it uninitialized. Without this,
# package signature checks fail and `pacman -Syu` won't run. This is the
# slow step (gpg wants entropy); it can take a minute on first run.
echo "[btw] initializing pacman keyring (this can take a minute)"
pacman-key --init && pacman-key --populate archlinuxarm

# A default non-root user for later graphical sessions. The terminal still
# opens as root under proot; this account just exists for when we need it.
if ! id btw >/dev/null 2>&1; then
  echo "[btw] creating default user 'btw'"
  useradd -m -s /bin/bash -G wheel btw
  passwd -d btw >/dev/null 2>&1
fi

echo "[btw] first-run setup done"
