package fr.doflegend.legendemu.object;


import fr.doflegend.legendemu.client.Characters;
import fr.doflegend.legendemu.common.ConditionParser;
import fr.doflegend.legendemu.common.Constant;
import fr.doflegend.legendemu.common.Formulas;
import fr.doflegend.legendemu.common.SQLManager;
import fr.doflegend.legendemu.common.SocketManager;
import fr.doflegend.legendemu.common.World;
import fr.doflegend.legendemu.common.World.Area;
import fr.doflegend.legendemu.common.World.SubArea;
import fr.doflegend.legendemu.fight.object.Prism;
import fr.doflegend.legendemu.fight.object.Monster.MobGroup;
import fr.doflegend.legendemu.game.GameServer;
import fr.doflegend.legendemu.kernel.Config;
import fr.doflegend.legendemu.object.NpcTemplates.NPC_question;
import fr.doflegend.legendemu.object.Objects.ObjTemplate;
import fr.doflegend.legendemu.object.job.Job.StatsMetier;


public class Action {

	private int ID;
	private String args;
	private String cond;
	
	public Action(int id, String args, String cond)
	{
		this.ID = id;
		this.args = args;
		this.cond = cond;
	}


	public void apply(Characters perso, Characters target, int itemID, int cellid)
	{
		if(perso == null)return;
		if(!cond.equalsIgnoreCase("") && !cond.equalsIgnoreCase("-1")&& !ConditionParser.validConditions(perso,cond))
		{
			SocketManager.GAME_SEND_Im_PACKET(perso, "119");
			return;
		}
		if(perso.get_compte().getGameThread() == null) return;
		fr.doflegend.legendemu.game.GameSendThread out = perso.get_compte().getGameThread().get_out();	
		switch(ID)
		{
		case -22: // Remettre prisonnier
			if(perso.get_followers() != "" && !perso.get_followers().isEmpty())
			{
				int skinFollower = Integer.parseInt(perso.get_followers());
				int questId = Constant.getQuestByMobSkin(skinFollower);
				GameServer.addToLog("associe a quete "  + questId);
				if(questId != -1)
				{
					perso.upgradeQuest(questId);
					perso.remove_follow();
					int itemFollow = Constant.getItemByMobSkin(skinFollower);
					perso.removeByTemplateID(itemFollow, 1);
				}
				//else
					//SocketManager.GAME_SEND_MESSAGE(perso, "Vous n'avez aucun prisonnier a remettre !", "FF0000");
			}
			//else
				//SocketManager.GAME_SEND_MESSAGE(perso, "Vous n'avez aucun prisonnier a remettre !", "FF0000");	
		break;
		case -2://cr�er guilde
			if(perso.is_away())return;
			if(perso.get_guild() != null || perso.getGuildMember() != null)
			{
				SocketManager.GAME_SEND_gC_PACKET(perso, "Ea");
				return;
			}
			if(perso.hasItemGuid(1575)) {
			SocketManager.GAME_SEND_gn_PACKET(perso);
			perso.removeByTemplateID(1575,-1);
			SocketManager.GAME_SEND_Im_PACKET(perso, "022;"+-1+"~"+1575);
			} else {
				SocketManager.GAME_SEND_MESSAGE(perso, "Pour pouvoir cr�er une guilde, il faut poss�der une Guildalogemme", Config.CONFIG_MOTD_COLOR);
			}
		break;
			case -1://Ouvrir banque
				//Sauvagarde du perso et des item avant.
				SQLManager.SAVE_PERSONNAGE(perso,true);
				if(perso.getDeshonor() >= 1) 
				{
					SocketManager.GAME_SEND_Im_PACKET(perso, "183");
					return;
				}
				int cost = perso.getBankCost();
				if(cost > 0)
				{
					long nKamas = perso.get_kamas() - cost;
					if(nKamas <0)//Si le joueur n'a pas assez de kamas pour ouvrir la banque
					{
						SocketManager.GAME_SEND_Im_PACKET(perso, "1128;"+cost);
						return;
					}
					perso.set_kamas(nKamas);
					SocketManager.GAME_SEND_STATS_PACKET(perso);
					SocketManager.GAME_SEND_Im_PACKET(perso, "020;"+cost);
				}
				SocketManager.GAME_SEND_ECK_PACKET(perso.get_compte().getGameThread().get_out(), 5, "");
				SocketManager.GAME_SEND_EL_BANK_PACKET(perso);
				perso.set_away(true);
				perso.setInBank(true);
			break;
			
			case 0://T�l�portation
				try
				{
					short newMapID = Short.parseShort(args.split(",",2)[0]);
					int newCellID = Integer.parseInt(args.split(",",2)[1]);
					
					perso.teleport(newMapID,newCellID);	
				}catch(Exception e ){return;};
			break;
			
			case 1://Discours NPC
				out = perso.get_compte().getGameThread().get_out();
				if(args.equalsIgnoreCase("DV"))
				{
					SocketManager.GAME_SEND_END_DIALOG_PACKET(out);
					perso.set_isTalkingWith(0);
				}else
				{
					int qID = -1;
					try
					{
						qID = Integer.parseInt(args);
					}catch(NumberFormatException e){};
					
					NPC_question  quest = World.getNPCQuestion(qID, perso);
					if(quest == null)
					{
						SocketManager.GAME_SEND_END_DIALOG_PACKET(out);
						perso.set_isTalkingWith(0);
						return;
					}
					SocketManager.GAME_SEND_QUESTION_PACKET(out, quest.parseToDQPacket(perso));
				}
			break;
			
			case 4://Kamas
				try
				{
					int count = Integer.parseInt(args);
					long curKamas = perso.get_kamas();
					long newKamas = curKamas + count;
					if(newKamas <0) newKamas = 0;
					perso.set_kamas(newKamas);
					
					//Si en ligne (normalement oui)
					if(perso.isOnline())
						SocketManager.GAME_SEND_STATS_PACKET(perso);
				}catch(Exception e){GameServer.addToLog(e.getMessage());};
			break;
			case 5://objet
				try
				{
					int tID = Integer.parseInt(args.split(",")[0]);
					int count = Integer.parseInt(args.split(",")[1]);
					boolean send = true;
					if(args.split(",").length >2)send = args.split(",")[2].equals("1");
					
					//Si on ajoute
					if(count > 0)
					{
						ObjTemplate T = World.getObjTemplate(tID);
						if(T == null)return;
						Objects O = T.createNewItem(count, false, -1);
						//Si retourne true, on l'ajoute au monde
						if(perso.addObjet(O, true))
							World.addObjet(O, true);
					}else
					{
						perso.removeByTemplateID(tID,-count);
					}
					//Si en ligne (normalement oui)
					if(perso.isOnline())//on envoie le packet qui indique l'ajout//retrait d'un item
					{
						SocketManager.GAME_SEND_Ow_PACKET(perso);
						if(send)
						{
							if(count >= 0){
								SocketManager.GAME_SEND_Im_PACKET(perso, "021;"+count+"~"+tID);
							}
							else if(count < 0){
								SocketManager.GAME_SEND_Im_PACKET(perso, "022;"+-count+"~"+tID);
							}
						}
					}
				}catch(Exception e){GameServer.addToLog(e.getMessage());};
			break;
			case 6://Apprendre un m�tier
				try
				{
					int mID = Integer.parseInt(args);
					if(World.getMetier(mID) == null)return;
					// Si c'est un m�tier 'basic' :
					if(mID == 	2 || mID == 11 ||
					   mID == 13 || mID == 14 ||
					   mID == 15 || mID == 16 ||
					   mID == 17 || mID == 18 ||
					   mID == 19 || mID == 20 ||
					   mID == 24 || mID == 25 ||
					   mID == 26 || mID == 27 ||
					   mID == 28 || mID == 31 ||
					   mID == 36 || mID == 41 ||
					   mID == 56 || mID == 58 ||
					   mID == 60 || mID == 65)
					{
						if(perso.getMetierByID(mID) != null)//M�tier d�j� appris
						{
							SocketManager.GAME_SEND_Im_PACKET(perso, "111");
						}
						
						if(perso.totalJobBasic() > 2)//On compte les m�tiers d�ja acquis si c'est sup�rieur a 2 on ignore
						{
							SocketManager.GAME_SEND_Im_PACKET(perso, "19");
						}else//Si c'est < ou = � 2 on apprend
						{
							perso.learnJob(World.getMetier(mID));
						}
					}
					// Si c'est une specialisations 'FM' :
					if(mID == 	43 || mID == 44 ||
					   mID == 45 || mID == 46 ||
					   mID == 47 || mID == 48 ||
					   mID == 49 || mID == 50 ||
					   mID == 62 || mID == 63 ||
					   mID == 64)
					{
						//M�tier simple level 65 n�cessaire
						if(perso.getMetierByID(17) != null && perso.getMetierByID(17).get_lvl() >= 65 && mID == 43
						|| perso.getMetierByID(11) != null && perso.getMetierByID(11).get_lvl() >= 65 && mID == 44
						|| perso.getMetierByID(14) != null && perso.getMetierByID(14).get_lvl() >= 65 && mID == 45
						|| perso.getMetierByID(20) != null && perso.getMetierByID(20).get_lvl() >= 65 && mID == 46
						|| perso.getMetierByID(31) != null && perso.getMetierByID(31).get_lvl() >= 65 && mID == 47
						|| perso.getMetierByID(13) != null && perso.getMetierByID(13).get_lvl() >= 65 && mID == 48
						|| perso.getMetierByID(19) != null && perso.getMetierByID(19).get_lvl() >= 65 && mID == 49
						|| perso.getMetierByID(18) != null && perso.getMetierByID(18).get_lvl() >= 65 && mID == 50
						|| perso.getMetierByID(15) != null && perso.getMetierByID(15).get_lvl() >= 65 && mID == 62
						|| perso.getMetierByID(16) != null && perso.getMetierByID(16).get_lvl() >= 65 && mID == 63
						|| perso.getMetierByID(27) != null && perso.getMetierByID(27).get_lvl() >= 65 && mID == 64)
						{
							//On compte les specialisations d�ja acquis si c'est sup�rieur a 2 on ignore
							if(perso.getMetierByID(mID) != null)//M�tier d�j� appris
							{
								SocketManager.GAME_SEND_Im_PACKET(perso, "111");
							}
							
							if(perso.totalJobFM() > 2)//On compte les m�tiers d�ja acquis si c'est sup�rieur a 2 on ignore
							{
								SocketManager.GAME_SEND_Im_PACKET(perso, "19");
							}
							else//Si c'est < ou = � 2 on apprend
							{
								perso.learnJob(World.getMetier(mID));
								perso.getMetierByID(mID).addXp(perso, 582000);//Level 100 direct
							}	
						}else
						{
							SocketManager.GAME_SEND_Im_PACKET(perso, "12");
						}
					}
				}catch(Exception e){GameServer.addToLog(e.getMessage());};
			break;
			case 7://retour au point de sauvegarde
				perso.warpToSavePos();
			break;
			case 8://Ajouter une Stat
				try
				{
					int statID = Integer.parseInt(args.split(",",2)[0]);
					int number = Integer.parseInt(args.split(",",2)[1]);
					perso.get_baseStats().addOneStat(statID, number);
					SocketManager.GAME_SEND_STATS_PACKET(perso);
					int messID = 0;
					switch(statID)
					{
						case Constant.STATS_ADD_INTE: messID = 14;break;
					}
					if(perso.CheckItemConditions() != 0)
					{
						SocketManager.GAME_SEND_Ow_PACKET(perso);
						perso.refreshStats();
						if(perso.getGroup() != null)
						{
							SocketManager.GAME_SEND_PM_MOD_PACKET_TO_GROUP(perso.getGroup(),perso);
						}
						SocketManager.GAME_SEND_STATS_PACKET(perso);
					}
					if(messID>0)
						SocketManager.GAME_SEND_Im_PACKET(perso, "0"+messID+";"+number);
				}catch(Exception e ){return;};
			break;
			case 9://Apprendre un sort
				try
				{
					int sID = Integer.parseInt(args);
					if(World.getSort(sID) == null)return;
					perso.learnSpell(sID,1, true,true);
				}catch(Exception e){GameServer.addToLog(e.getMessage());};
			break;
			case 10://Pain/potion/viande/poisson
				try
				{
					int min = Integer.parseInt(args.split(",",2)[0]);
					int max = Integer.parseInt(args.split(",",2)[1]);
					if(max == 0) max = min;
					int val = Formulas.getRandomValue(min, max);
					if(target != null)
					{
						if(target.get_PDV() + val > target.get_PDVMAX())val = target.get_PDVMAX()-target.get_PDV();
						target.set_PDV(target.get_PDV()+val);
						SocketManager.GAME_SEND_STATS_PACKET(target);
					}
					else
					{
						if(perso.get_PDV() + val > perso.get_PDVMAX())val = perso.get_PDVMAX()-perso.get_PDV();
						perso.set_PDV(perso.get_PDV()+val);
						SocketManager.GAME_SEND_STATS_PACKET(perso);
					}
				}catch(Exception e){GameServer.addToLog(e.getMessage());};
			break;
			case 11://Definir l'alignement
				try
				{
					byte newAlign = Byte.parseByte(args.split(",",2)[0]);
					boolean replace = Integer.parseInt(args.split(",",2)[1]) == 1;
					//Si le perso n'est pas neutre, et qu'on doit pas remplacer, on passe
					if(perso.get_align() != Constant.ALIGNEMENT_NEUTRE && !replace)return;
					perso.modifAlignement(newAlign);
				}catch(Exception e){GameServer.addToLog(e.getMessage());};
			break;
			/* TODO: autres actions */
			case 12://Spawn d'un groupe de monstre
				try
				{
					boolean delObj = args.split(",")[0].equals("true");
					boolean inArena = args.split(",")[1].equals("true");
					if(inArena && !World.isArenaMap(perso.get_curCarte().get_id()))return;	//Si la map du personnage n'est pas class� comme �tant dans l'ar�ne

					SoulStone pierrePleine = (SoulStone)World.getObjet(itemID);

					String groupData = pierrePleine.parseGroupData();
					String condition = "MiS = "+perso.get_GUID();	//Condition pour que le groupe ne soit lan�able que par le personnage qui � utiliser l'objet
					perso.get_curCarte().spawnNewGroup(true, perso.get_curCell().getID(), groupData,condition);

					if(delObj)
					{
						perso.removeItem(itemID, 1, true, true);
					}
				}catch(Exception e){GameServer.addToLog(e.getMessage());};
			break;
		    case 13: //Reset Carac
		        try
		        {
		          perso.get_baseStats().addOneStat(125, -perso.get_baseStats().getEffect(125));
		          perso.get_baseStats().addOneStat(124, -perso.get_baseStats().getEffect(124));
		          perso.get_baseStats().addOneStat(118, -perso.get_baseStats().getEffect(118));
		          perso.get_baseStats().addOneStat(123, -perso.get_baseStats().getEffect(123));
		          perso.get_baseStats().addOneStat(119, -perso.get_baseStats().getEffect(119));
		          perso.get_baseStats().addOneStat(126, -perso.get_baseStats().getEffect(126));
		          perso.addCapital((perso.get_lvl() - 1) * 5 - perso.get_capital());
					if(perso.CheckItemConditions() != 0)
					{
						SocketManager.GAME_SEND_Ow_PACKET(perso);
						perso.refreshStats();
						if(perso.getGroup() != null)
						{
							SocketManager.GAME_SEND_PM_MOD_PACKET_TO_GROUP(perso.getGroup(),perso);
						}
					}
		          SocketManager.GAME_SEND_STATS_PACKET(perso);
		        }catch(Exception e){GameServer.addToLog(e.getMessage());};
		    break;
		    case 14://Ouvrir l'interface d'oublie de sort
		    	perso.setisForgetingSpell(true);
				SocketManager.GAME_SEND_FORGETSPELL_INTERFACE('+', perso);
			break;
			case 15://T�l�portation donjon
				try
				{
					short newMapID = Short.parseShort(args.split(",")[0]);
					int newCellID = Integer.parseInt(args.split(",")[1]);
					int ObjetNeed = Integer.parseInt(args.split(",")[2]);
					int MapNeed = Integer.parseInt(args.split(",")[3]);
					if(ObjetNeed == 0)
					{
						//T�l�portation sans objets
						perso.teleport(newMapID,newCellID);
					}else if(ObjetNeed > 0)
					{
					if(MapNeed == 0)
					{
						//T�l�portation sans map
						perso.teleport(newMapID,newCellID);
					}else if(MapNeed > 0)
					{
					if (perso.hasItemTemplate(ObjetNeed, 1) && perso.get_curCarte().get_id() == MapNeed)
					{
						//Le perso a l'item
						//Le perso est sur la bonne map
						//On t�l�porte, on supprime apr�s
						perso.teleport(newMapID,newCellID);
						perso.removeByTemplateID(ObjetNeed, 1);
						SocketManager.GAME_SEND_Ow_PACKET(perso);
					}
					else if(perso.get_curCarte().get_id() != MapNeed)
					{
						//Le perso n'est pas sur la bonne map
						SocketManager.GAME_SEND_MESSAGE(perso, "Vous n'etes pas sur la bonne map du donjon pour etre teleporter.", "009900");
					}
					else
					{
						//Le perso ne poss�de pas l'item
						SocketManager.GAME_SEND_MESSAGE(perso, "Vous ne possedez pas la clef necessaire.", "009900");
					}
					}
					}
				}catch(Exception e){GameServer.addToLog(e.getMessage());};
			break;
			case 16://Ajout d'honneur HonorValue
				try
				{
					if(perso.get_align() != 0)
					{
						int AddHonor = Integer.parseInt(args);
						int ActualHonor = perso.get_honor();
						perso.set_honor(ActualHonor+AddHonor);
					}
				}catch(Exception e){GameServer.addToLog(e.getMessage());};
			break;
			case 17://Xp m�tier JobID,XpValue
				try
				{
					int JobID = Integer.parseInt(args.split(",")[0]);
					int XpValue = Integer.parseInt(args.split(",")[1]);
					if(perso.getMetierByID(JobID) != null)
					{
						perso.getMetierByID(JobID).addXp(perso, XpValue);
					}
				}catch(Exception e){GameServer.addToLog(e.getMessage());};
			break;
			case 18://T�l�portation chez sois
				if(Houses.AlreadyHaveHouse(perso))//Si il a une maison
				{
					Objects obj = World.getObjet(itemID);
					if (perso.hasItemTemplate(obj.getTemplate().getID(), 1))
					{
						perso.removeByTemplateID(obj.getTemplate().getID(),1);
						Houses h = Houses.get_HouseByPerso(perso);
						if(h == null) return;
						perso.teleport((short)h.get_mapid(), h.get_caseid());
					}
				}
			break;
			case 19://T�l�portation maison de guilde (ouverture du panneau de guilde)
				SocketManager.GAME_SEND_GUILDHOUSE_PACKET(perso);
			break;
			case 20://+Points de sorts
				try
				{
					int pts = Integer.parseInt(args);
					if(pts < 1) return;
					perso.addSpellPoint(pts);
					SocketManager.GAME_SEND_STATS_PACKET(perso);
				}catch(Exception e){GameServer.addToLog(e.getMessage());};
			break;
			case 21://+Energie
				try
				{
					int Energy = Integer.parseInt(args);
					if(Energy < 1) return;
					
					int EnergyTotal = perso.get_energy()+Energy;
					if(EnergyTotal > 10000) EnergyTotal = 10000;
					
					perso.set_energy(EnergyTotal);
					SocketManager.GAME_SEND_STATS_PACKET(perso);
				}catch(Exception e){GameServer.addToLog(e.getMessage());};
			break;
			case 22://+Xp
				try
				{
					long XpAdd = Integer.parseInt(args);
					if(XpAdd < 1) return;
					
					long TotalXp = perso.get_curExp()+XpAdd;
					perso.set_curExp(TotalXp);
					SocketManager.GAME_SEND_STATS_PACKET(perso);
				}catch(Exception e){GameServer.addToLog(e.getMessage());};
			break;
			case 23://UnlearnJob
				try
				{
					int Job = Integer.parseInt(args);
					if(Job < 1) return;
					StatsMetier m = perso.getMetierByID(Job);
					if(m == null) return;
					perso.unlearnJob(m.getID());
					SocketManager.GAME_SEND_STATS_PACKET(perso);
					SQLManager.SAVE_PERSONNAGE(perso, false);
				}catch(Exception e){GameServer.addToLog(e.getMessage());};
			break;
			case 24://SimpleMorph
				try
				{
					int morphID = Integer.parseInt(args);
					if(morphID < 0)return;
					perso.set_gfxID(morphID);
					SocketManager.GAME_SEND_ERASE_ON_MAP_TO_MAP(perso.get_curCarte(), perso.get_GUID());
					SocketManager.GAME_SEND_ADD_PLAYER_TO_MAP(perso.get_curCarte(), perso);
				}catch(Exception e){GameServer.addToLog(e.getMessage());};
			break;
			case 25://SimpleUnMorph
				int UnMorphID = perso.get_classe()*10 + perso.get_sexe();
				perso.set_gfxID(UnMorphID);
				SocketManager.GAME_SEND_ERASE_ON_MAP_TO_MAP(perso.get_curCarte(), perso.get_GUID());
				SocketManager.GAME_SEND_ADD_PLAYER_TO_MAP(perso.get_curCarte(), perso);
			break;
			case 26://T�l�portation enclo de guilde (ouverture du panneau de guilde)
				SocketManager.GAME_SEND_GUILDENCLO_PACKET(perso);
			break;
			case 27://startFigthVersusMonstres args : monsterID,monsterLevel| ...
				String ValidMobGroup = "";
				try
		        {
					for(String MobAndLevel : args.split("\\|"))
					{
						int monsterID = -1;
						int monsterLevel = -1;
						String[] MobOrLevel = MobAndLevel.split(",");
						monsterID = Integer.parseInt(MobOrLevel[0]);
						monsterLevel = Integer.parseInt(MobOrLevel[1]);
						
						if(World.getMonstre(monsterID) == null || World.getMonstre(monsterID).getGradeByLevel(monsterLevel) == null)
						{
							if(Config.debug) GameServer.addToLog("Monstre invalide : monsterID:"+monsterID+" monsterLevel:"+monsterLevel);
							continue;
						}
						ValidMobGroup += monsterID+","+monsterLevel+","+monsterLevel+";";
					}
					if(ValidMobGroup.isEmpty()) return;
					MobGroup group  = new MobGroup(perso.get_curCarte().get_nextObjectID(),perso.get_curCell().getID(),ValidMobGroup);
					perso.get_curCarte().startFigthVersusMonstres(perso, group);
		        }catch(Exception e){GameServer.addToLog(e.getMessage());};
			break;
		    case 64: // Activer une cell pour un IO
		        try {
		          short id = Short.parseShort(this.args);
		          Maps map = perso.get_curCarte();
		          if (map.getCase(id) == null)
		            SocketManager.GAME_SEND_BN(out);
		          else
		            map.getCase(id).activeCell();
		        }
		        catch (Exception e) {
		          e.printStackTrace();
		        }
		        return;
		    case 65: // Activer une cell pour le dj d'incarnam
		        try {
		          short id = Short.parseShort(this.args);
		          Maps map = perso.get_curCarte();
		          if (map.getCase(id) == null)
		            SocketManager.GAME_SEND_BN(out);
		          else
		            map.getCase(id).activeIncarnamCell();
		        }
		        catch (Exception e) {
		          e.printStackTrace();
		        }
		        return;
			case 100://Donner l'abilit� 'args' � une dragodinde
                Mount mount = perso.getMount();
                World.addDragodinde(
                  new Mount(
                 mount.get_id(),
                 mount.get_color(),
                 mount.get_sexe(),
                 mount.get_amour(),
                 mount.get_endurance(),
                 mount.get_level(),
                 mount.get_exp(),
                 mount.get_nom(),
                 mount.get_fatigue(),
                 mount.get_energie(),
                 mount.get_reprod(),
                 mount.get_maturite(),
                 mount.get_serenite(),
                 mount.parseObjDB(),
                 mount.get_ancetres(),
                 args));
                 perso.setMount(World.getDragoByID(mount.get_id()));
                 SocketManager.GAME_SEND_Re_PACKET(perso, "+", World.getDragoByID(mount.get_id()));
                 SQLManager.UPDATE_MOUNT_INFOS(mount);
                 break;
			case 101://Arriver sur case de mariage
				if((perso.get_sexe() == 0 && perso.get_curCell().getID() == 282) || (perso.get_sexe() == 1 && perso.get_curCell().getID() == 297))
				{
					World.AddMarried(perso.get_sexe(), perso);
				}else 
				{
					SocketManager.GAME_SEND_Im_PACKET(perso, "1102");
				}
			break;
			case 102://Marier des personnages
				World.PriestRequest(perso, perso.get_curCarte(), perso.get_isTalkingWith());
			break;
			case 103://Divorce
				if(perso.get_kamas() < 50000)
				{
					return;
				}else
				{
					perso.set_kamas(perso.get_kamas()-50000);
					Characters wife = World.getPersonnage(perso.getWife());
					wife.Divorce();
					perso.Divorce();
				}
			break;
			case 104://Don d'objet (cadeau � la connexion, arg : IDdelitem
				int item = Integer.parseInt(args); 
				perso.get_compte().setCadeau(item);
				SocketManager.GAME_SEND_MESSAGE(perso, "Vous avez re�u un cadeau sur votre compte !", Config.CONFIG_MOTD_COLOR);
			break;
			case 228://Faire animation Hors Combat
				try
				{
					int AnimationId = Integer.parseInt(args);
					Hustle animation = World.getAnimation(AnimationId);
					if(perso.get_fight() != null) return;
					perso.changeOrientation(1);
					SocketManager.GAME_SEND_GA_PACKET_TO_MAP(perso.get_curCarte(), "0", 228, perso.get_GUID()+";"+cellid+","+Hustle.PrepareToGA(animation), "");
				}catch(Exception e){GameServer.addToLog(e.getMessage());};
			break;
			default:
				GameServer.addToLog("Action ID="+ID+" non implantee");
			break;
			case 43://Jouer une cin�matique
				String num = args;
				SocketManager.GAME_SEND_CIN_Packet(perso, num);
			break;
			case 44://Ouvrir un livre.
				String infoi = args;
				SocketManager.GAME_SEND_dCK_PACKET(perso, infoi);
	       break;
			case 170: // Ajouter un titre au joueur : Taparisse
				try
			    {	
		
			
					int titulo = Integer.parseInt(args); 
					target= World.getPersoByName(perso.get_name());
					target.set_title(titulo);
					
                    
			    	
			    	    SocketManager.GAME_SEND_MESSAGE(perso, "<b>Tu poss�des un nouveau titre !</b>", Config.CONFIG_MOTD_COLOR);
						SocketManager.GAME_SEND_STATS_PACKET(perso);
						SQLManager.SAVE_PERSONNAGE(perso, false);
					}catch(Exception e){GameServer.addToLog(e.getMessage());};
				
			    break;
			case 201:// Poser un Prisme
				try {
					int cellperso = perso.get_curCell().getID();
					Maps tCarte = perso.get_curCarte();
					SubArea subarea = tCarte.getSubArea();
					Area area = subarea.getArea();
					int alignement = perso.get_align();
					if (cellperso <= 0) {
						break;
					}
					if(Config.getWhenHasPosePrism().containsKey(1)){
						{
					
							if(System.currentTimeMillis() - (Config.getWhenHasPosePrism().get(1) + (Config.PRISMES_DELAIS_NEW_POSE*1000*60)) < 0)
						    {
					            SocketManager.GAME_SEND_MESSAGE(perso, "<b>Vous avez pos� un total de 4 prisme durant les derni�res "+Config.PRISMES_DELAIS_NEW_POSE+" minutes !</b>", "000000");
					            break;
					        }else
							{
					        	Config.getWhenHasPosePrism().put(1, (long)-1);//on reinitialise
					        	Config.setPosePrism(0);
							}
						}
					}
					if (alignement == 0 || alignement == 3) {
						SocketManager.GAME_SEND_MESSAGE(perso,"Vous ne poss�dez pas l'alignement n�cessaire pour poser un prisme.", Config.CONFIG_MOTD_COLOR);
						break;
					}
					if (!perso.is_showWings()) {
						SocketManager.GAME_SEND_MESSAGE(perso,"Vos ailes doivent �tre actives afin de poser un prisme.", Config.CONFIG_MOTD_COLOR);
						break;
					}
					if (Config.CartesWithoutPrismes != null && Config.CartesWithoutPrismes.contains(String.valueOf(tCarte.get_id()))) {
						SocketManager.GAME_SEND_MESSAGE(perso, "Vous ne pouvez pas poser un prisme sur cette map.", Config.CONFIG_MOTD_COLOR);
						break;
					}
					if ((subarea.getalignement() != 0 && subarea.getalignement() != -1) || !subarea.getConquistable()) {
						SocketManager.GAME_SEND_MESSAGE(perso,"L'alignement de cette sous-zone est en conqu�te ou n'est pas neutre !", Config.CONFIG_MOTD_COLOR);
						break;
					}
					Prism Prisme = new Prism(World.getNextIDPrisme(), alignement, 1, tCarte.get_id(), cellperso, 0, -1);
					subarea.setalignement(alignement);
					subarea.setPrismeID(Prisme.getID());
					for (Characters z : World.getOnlinePersos()) {
						if (z == null)
							continue;
						if (z.get_align() == 0) {
							SocketManager.GAME_SEND_am_ALIGN_PACKET_TO_SUBAREA(z, subarea.getID() + "|" + alignement + "|1");
							if (area.getalignement() == 0)
								SocketManager.GAME_SEND_aM_ALIGN_PACKET_TO_AREA(z, area.getID() + "|" + alignement);
							continue;
						}
						SocketManager.GAME_SEND_am_ALIGN_PACKET_TO_SUBAREA(z, subarea.getID() + "|" + alignement + "|0");
						if (area.getalignement() == 0)
							SocketManager.GAME_SEND_aM_ALIGN_PACKET_TO_AREA(z, area.getID() + "|" + alignement);
					}
					if (area.getalignement() == 0) {
						area.setPrismeID(Prisme.getID());
						area.setalignement(alignement);
						Prisme.setAreaConquest(area.getID());
					}
					World.addPrisme(Prisme);
					SQLManager.ADD_PRISME(Prisme);
					Config.setPosePrism(Config.getPosePrism()+1);
					if (Config.getPosePrism() >= 4)
						Config.getWhenHasPosePrism().put(1, System.currentTimeMillis());
					SocketManager.GAME_SEND_PRISME_TO_MAP(tCarte, Prisme);
				} catch (Exception e) {}
				break;

			case 257://Monstre Pain
				try
				{
					boolean delObj = args.split(",")[0].equals("true");
					
					String groupData = "2787,1,200;";
					String condition = "MiS = "+perso.get_GUID();	//Condition pour que le groupe ne soit lan�able que par le personnage qui � utiliser l'objet
					perso.get_curCarte().spawnNewGroup(true, perso.get_curCell().getID(), groupData,condition);

					if(delObj)
					{
						perso.removeItem(itemID, 1, true, true);
					}
	                }catch(Exception e){GameServer.addToLog(e.getMessage());};	              
	                
			case 258://Abragland
				try
				{
					boolean delObj = args.split(",")[0].equals("true");
					boolean inZone = args.split(",")[1].equals("true");
					
					if(inZone && !World.isAbraMap(perso.get_curCarte().get_id()))
					return;
					
	                String groupData = "2771,23,39;";
					String condition = "MiS = "+perso.get_GUID();	//Condition pour que le groupe ne soit lan�able que par le personnage qui � utiliser l'objet
					perso.get_curCarte().spawnNewGroup(true, perso.get_curCell().getID(), groupData,condition);

					if(delObj)
					{
						perso.removeItem(itemID, 1, true, true);
					}
				}catch(Exception e){GameServer.addToLog(e.getMessage());};
				
			case 259://Craqueloroche
				try
				{
					boolean inZone = args.split(",")[1].equals("true");
					boolean delObj = args.split(",")[0].equals("true");
					
					if(inZone && !World.isCraqueleurMap(perso.get_curCarte().get_id()))
					return;
					
					String groupData = "2789,20,28;";
					String condition = "MiS = "+perso.get_GUID();	//Condition pour que le groupe ne soit lan�able que par le personnage qui � utiliser l'objet
					perso.get_curCarte().spawnNewGroup(true, perso.get_curCell().getID(), groupData,condition);

					if(delObj)
					{
						perso.removeItem(itemID, 1, true, true);
					}
				}catch(Exception e){GameServer.addToLog(e.getMessage());};
				
			case 260://T�l�portation en parlant a un pnj avec level minimum requis : Taparisse
				try
				{
					short newMapID = Short.parseShort(args.split(",")[0]);
					int newCellID = Integer.parseInt(args.split(",")[1]);
					int levelperso = Integer.parseInt(args.split(",")[2]);
					
					if (perso.get_lvl() >= levelperso)
					{
						perso.teleport(newMapID,newCellID);
					}
					else 
				{
						SocketManager.GAME_SEND_MESSAGE(perso, "Vous n'avez pas le level requis : "+levelperso+".", Config.CONFIG_MOTD_COLOR);
				}
			
				}catch(Exception e ){return;};
			}
		}
	
	public int getID()
	{
		return ID;
	}
}
