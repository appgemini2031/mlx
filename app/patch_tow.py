new_code = '''
        // PUSAT KONEKSI JARINGAN 2-HP SERAGAM (UNTUK SEMUA 4 GAME)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = androidx.compose.foundation.BorderStroke(0.8.dp, DarkCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isBigo = brokerConfig.activePlatform.equals("BIGO", ignoreCase = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (!isBigo) Color(0xFFBE185D) else DarkSurfaceVariant)
                                .clickable { receiver.switchPlatform("TIKTOK") }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🎵 TikTok", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isBigo) Color(0xFF0891B2) else DarkSurfaceVariant)
                                .clickable { receiver.switchPlatform("BIGO") }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🫘 Bigo", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        text = if (isConnected) "🟢 Online: $connDetails" else "🟡 Siaga (Port 8765)",
                        color = if (isConnected) EmeraldGreen else Color(0xFFFCD34D),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = ipInput,
                        onValueChange = { ipInput = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.5.sp),
                        placeholder = { Text("127.0.0.1 / IP Wi-Fi", fontSize = 10.sp, color = Color.Gray) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Button(
                        onClick = {
                            prefs.updateServerAddress(ipInput.trim())
                            receiver.connect()
                        },
                        modifier = Modifier.height(38.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isConnected) EmeraldGreen else NeonCyan,
                            contentColor = Color(0xFF090D16)
                        )
                    ) {
                        Text(if (isConnected) "Tersambung" else "Sambung", fontSize = 10.5.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        if (selectedGameIndex == 3) {
            TrooperRoyaleControllerContent(
                onLaunchFullScreenArena = {
                    com.tajasb.game.InteractiveTrooperRoyaleEngine.getInstance(context).setArenaDisplayActive(true)
                }
            )
        } else if (selectedGameIndex == 2) {
            BeyBattleControllerContent(
                onLaunchFullScreenArena = {
                    com.tajasb.game.InteractiveBeyBattleEngine.getInstance(context).setArenaDisplayActive(true)
                }
            )
        } else if (selectedGameIndex == 1) {
            KingOfThroneControllerContent(
                onLaunchFullScreenArena = {
                    com.tajasb.game.InteractiveKingOfThroneEngine.getInstance(context).setArenaDisplayActive(true)
                }
            )
        } else {
            // GAME 1: TARIK TAMBANG — KARTU SERAGAM COMPACT
            StandardGameCard(
                gameIcon = "⚔️",
                gameTitle = "Tarik Tambang",
                gameSubtitle = "Duel 2 Kubu Tarik Tali Live",
                isVcamActive = state.isEnabled,
                onToggleVcam = {
                    val next = engine.toggleEnabled()
                    Toast.makeText(
                        context,
                        if (next) "Overlay VCam: ON" else "Overlay VCam: OFF",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onLaunchFullScreen = {
                    if (connStatus == ConnectionStatus.DISCONNECTED) {
                        receiver.connect()
                    }
                    onLaunchFullScreenArena()
                },
                likeAction = "Tarik Ringan",
                chatAction = "Ketik 1/2 (+${state.chatWeight.toInt()})",
                giftAction = "Tarik Sultan (${state.giftMultiplier.toInt()}x)",
                statusText = "Ronde ${state.currentRound} • ${state.teamAName} [${state.teamAWins}] vs [${state.teamBWins}] ${state.teamBName} • Tali: ${state.teamAPercent}% - ${state.teamBPercent}%",
                isRunning = state.isAutoTraffic,
                onToggleRunning = {
                    val active = engine.toggleAutoTraffic()
                    Toast.makeText(context, if (active) "Auto: ON" else "Auto: OFF", Toast.LENGTH_SHORT).show()
                },
                onReset = { engine.resetRope() },
                botButtonLabel = if (state.isAutoTraffic) "Auto: ON" else "Auto: OFF",
                onBotAction = {
                    val active = engine.toggleAutoTraffic()
                    Toast.makeText(context, if (active) "Auto: ON" else "Auto: OFF", Toast.LENGTH_SHORT).show()
                },
                giftTestButtonLabel = "+10 Koin Gift",
                onGiftTestAction = {
                    engine.manualPull("A", state.giftMultiplier * 10f, "Sultan Kiri", "🎁 10 Koin")
                },
                onOpenSettings = { showSettingsDialog = true },
                accentColor = NeonCyan
            )

            if (showSettingsDialog) {
                UnifiedGameSettingsDialog(
                    gameTitle = "Pengaturan Tarik Tambang",
                    onDismiss = { showSettingsDialog = false },
                    onShiftX = {},
                    onShiftY = {},
                    onScaleChange = {},
                    scalePercent = 100,
                    customSettingsContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "🏷️ Pilih Cepat Nama Kubu:",
                                color = Color(0xFFCBD5E1),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val presets = listOf(
                                    Triple("Cowok vs Cewek", "COWOK", "CEWEK"),
                                    Triple("Biru vs Merah", "TIM BIRU", "TIM MERAH"),
                                    Triple("Kiri vs Kanan", "KIRI", "KANAN"),
                                    Triple("Kucing vs Anjing", "KUCING", "ANJING"),
                                    Triple("Tim A vs Tim B", "TIM A", "TIM B")
                                )
                                presets.forEach { (label, tA, tB) ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(DarkSurfaceVariant)
                                            .border(0.7.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                            .clickable {
                                                customTeamA = tA
                                                customTeamB = tB
                                                engine.setTeamNames(tA, tB)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = customTeamA,
                                    onValueChange = { customTeamA = it },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.5.sp),
                                    label = { Text("Tim Kiri (1)", fontSize = 9.5.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = customTeamB,
                                    onValueChange = { customTeamB = it },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.5.sp),
                                    label = { Text("Tim Kanan (2)", fontSize = 9.5.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Button(
                                onClick = {
                                    engine.setTeamNames(customTeamA, customTeamB)
                                    Toast.makeText(context, "Nama kubu disimpan!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth().height(34.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Simpan Nama Kubu", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF090D16))
                            }

                            Text(
                                text = "🎯 Target Poin Menang (Saat ini: ${state.pointsToWin.toInt()}):",
                                color = Color(0xFFCBD5E1),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(250f, 500f, 1000f, 2500f, 5000f).forEach { pts ->
                                    val selected = state.pointsToWin.toInt() == pts.toInt()
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (selected) NeonCyan.copy(alpha = 0.22f) else DarkSurfaceVariant)
                                            .border(0.8.dp, if (selected) NeonCyan else DarkCardBorder, RoundedCornerShape(6.dp))
                                            .clickable { engine.setPointsToWin(pts) }
                                            .padding(vertical = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("${pts.toInt()}", color = if (selected) NeonCyan else Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = { engine.startNewRound() },
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Ronde Baru", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { engine.resetScores() },
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C)),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Reset Skor", fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                )
            }
        }
'''

with open('app/src/main/java/com/tajasb/ui/screens/TugOfWarArenaScreen.kt', 'r') as f:
    lines = f.readlines()

# line 1135 is index 1134, line 1745 is index 1744
before = lines[:1134]
after = lines[1745:]

with open('app/src/main/java/com/tajasb/ui/screens/TugOfWarArenaScreen.kt', 'w') as f:
    f.writelines(before)
    f.write(new_code)
    f.writelines(after)

print('Successfully updated TugOfWarArenaScreen.kt')
