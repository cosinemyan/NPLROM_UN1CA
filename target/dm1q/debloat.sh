#
# Copyright (C) 2025 Salvo Giangreco
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.
#

# Debloat list for Galaxy S23 (Snapdragon) (dm1q)
# - Add entries inside the specific partition containing that file (<PARTITION>_DEBLOAT+="")
# - DO NOT add the partition name at the start of any entry (eg. "/system/dpolicy_system")
# - DO NOT add a slash at the start of any entry (eg. "/dpolicy_system")

# SKT,KT,LG
SYSTEM_DEBLOAT+="
system/app/KTAuth_Stub
system/app/KTCustomerService
system/app/LGUMiniCustomerCenter
system/app/LGUplusTsmProxy
system/app/SKTMemberShip_new
system/app/TWorld
system/priv-app/KTOneStore
system/priv-app/KTServiceAgent
system/priv-app/KTServiceMenu
system/priv-app/LGUOZStore
system/priv-app/OneStoreService
system/priv-app/SKTOneStore
system/priv-app/TPhoneOnePackage
system/priv-app/TService
"

#I don't need
SYSTEM_DEBLOAT+="
system/app/BookmarkProvider
system/app/Fast
system/app/KidsHome_Installer
system/app/Rampart
system/app/SmartManager_v6_DeviceSecurity
system/priv-app/AREmoji
system/priv-app/HybridRadio
system/priv-app/QRreader
system/priv-app/SOAgent77
system/priv-app/SPPPushClient
system/priv-app/SamsungMagnifier3
system/priv-app/SwiftkeyIme
system/priv-app/SwiftkeySetting
"