function Target_coef = Target(Target_type);
% Target_coef = Target(Target_type);
% input for allen98 search engine
% downwindlee_slope = Target_coef(1,1); 			% (percent)of wind speed at 10 m
% downwindlee_y_intercept = Target_coef(1,2);	% cm/s
% downwindlee_stdev = Target_coef(1,3);			% cm/s
% poscrosswindlee_slope = Target_coef(1,4);
% poscrosswindlee_y_intercept = Target_coef(1,5);
% poscrosswindlee_stdev = Target_coef(1,6);
% negcrosswindlee_slope = Target_coef(1,7);
% negcrosswindlee_y_intercept = Target_coef(1,8);
% negcrosswindlee_stdev = Target_coef(1,9);
% intercept_windsp = Target_coef(1,10);	% m/s wind speed at cross-over point of two CWL eqs.
% crosswind_rule = Target_coef(1,11);  	% +1 use +CWL below cross-over wind speed, -1 use -CWL, 0 use both 
% jibing_type = Target_coef(1,12);       % Resistance to jibing; 1 = low,2 = mod; 3 = high 

switch Target_type
   
case 'TEST'
   DWL = input('Slope of DWL (% wind)   ')
   CWL = input('Slope of CWL (% wind)   ')
   Syx = input('Std. Error Term (cm/s)  ') 
   
   Target_coef = ([DWL,0.00,Syx,CWL,0.0,Syx,-1.*CWL,0.0,Syx,0,0,1]);  % Change values to suit
  case 'TEST_3_15_3' 
     Target_coef = ([3.0,0.00,3.0,1.5,0.0,3.0,-1.5,0.0,3.0,0,0,1]);  % DWL = 3% CWL = 1.5% Syx =3cm/s
  case 'TEST_3_15_1' 
     Target_coef = ([3.0,0.00,1.0,1.5,0.0,1.0,-1.5,0.0,1.0,0,0,1]);  % DWL = 3% CWL = 1.5% Syx =1cm/s
  case 'TEST_1_1_1' 
     Target_coef = ([1.0,0.00,1.0,1.0,0.0,1.0,-1.0,0.0,1.0,0,0,1]);  % DWL = 1% CWL = 1.0% Syx =1cm/s
  case 'TEST_5_0_15' 
     % Target_num 68
     Target_coef = ([5.0,0.00,15.4333./1.1774,0.0,0.0,15.4333./1.1774,0.0,0.0,15.4333./1.1774,0,0,1]);  % DWL = 5% CWL = 5.0% Syx =15.4333cm/s = 0.3kts
     % Target_coef = ([3.5350,0.00,15,3.5350,0.0,15,-3.5350,0.0,15,0,0,1]);  % DWL = 3.53% CWL = 3.53% Syx =15.4333cm/s = 0.3kts
     % Target_coef = ([3.5350,0.00,3,3.5350,0.0,3,-3.5350,0.0,3,0,0,1]);  % DWL = 3.53% CWL = 3.53% Syx =15.4333cm/s = 0.3kts

     %Target_coef = ([3.5350,0.00,15.4333./1.1774,3.5350,0.0,15.4333./1.1774,-3.5350,0.0,15.4333./1.1774,0,0,1]);  % DWL = 3.53% CWL = 3.53% Syx =15.4333cm/s = 0.3kts
       case 'TEST_5_45_15' 
     % Target_num 68
      Target_coef = ([3.5350,0.00,15,3.5350,0.0,15,-3.5350,0.0,15,0,0,1]);  % DWL = 3.53% CWL = 3.53% Syx =15cm/s 
       case 'TEST_5_45_3' 
     % Target_num 68
      Target_coef = ([3.5350,0.00,3,3.5350,0.0,3,-3.5350,0.0,3,0,0,1]);  % DWL = 3.53% CWL = 3.53% Syx =3cm/s 

     
  case 'TEST_drogue'
     Target_coef = ([5.0,0.00,15.4333./1.1774,3.0,0.0,15.4333./1.1774,-3.0,0.0,15.4333./1.1774,0,0,2]);
  case 'TEST_7_0_15' 
     % Target_coef = ([7.0,0.00,15.4333./1.1774,0.0,0.0,15.4333./1.1774,0.0,0.0,15.4333./1.1774,0,0,1]);  % DWL = 7% CWL = 0.0% Syx =15.4333cm/s = 0.3kts
     Target_coef = ([7.0,0.00,21.5880./1.1774,0.0,0.0,21.58803./1.1774,0.0,0.0,21.5880./1.1774,0,0,1]);  % DWL = 7% CWL = 0.0% Syx =15.4333cm/s = 0.3kts

     
case 'PIW'
   Target_coef = ([0.96,0.0,12.0,0.54,0.0,9.4,-0.54,0.0,9.4,0,0,1]);
	case 'PIW_Vertical'
   	Target_coef = ([0.48,0.0,8.3,0.15,0.0,6.7,-0.15,0.0,6.7,0,0,1]);
	case 'PIW_Sitting'
      Target_coef = ([1.60,-3.98,2.42,0.13,0.33,2.11,-0.13,-0.33,2.11,0,0,1]);  
 	case 'PIW_Survival_Suit'
      Target_coef = ([1.71,1.12,3.93,1.36,-3.30,1.71,-0.13,-2.65,1.62,0,0,1]); 
   case 'PIW_Scuba_Suit'
      Target_coef = ([0.63,0.0,5.3,0.31,0.0,4.5,-0.31,0.0,4.5,0,0,1]);
   case 'PIW_Deceased'
      Target_coef = ([1.30,0.0,8.3,0.74,0.0,6.7,-0.74,0.0,6.7,0,0,1]);
      
      

  case 'SC_MLR_NoB'			%SurvivalCraft_MaritimeLifeRafts_NoBallastsystems
   Target_coef = ([3.70,0.0,12.0,1.98,0.0,9.4,-1.98,0.0,9.4,0,0,1]);
     	case 'SC_MLR_NoB_NoC_NoD'	%NoCanopy_NoDrogue
    		Target_coef = ([5.34,9.91,9.82,2.26,1.04,9.08,-2.26,-1.04,9.08,0,0,1]);
		case 'SC_MLR_NoB_NoC_D'	%NoCanopy_Drogue
   		Target_coef = ([3.15,-4.47,3.96,1.51,0.0,5.02,-1.51,0.0,5.02,0,0,1]);
		case 'SC_MLR_NoB_C_NoD'	%Canopy_NoDrogue
   		Target_coef = ([3.39,0.0,2.4,1.49,0.0,2.4,-1.49,0.0,2.4,0,0,1]);
		case 'SC_MLR_NoB_C_D'	%Canopy_Drogue
         Target_coef = ([2.65,0.0,12.0,1.42,0.0,9.4,-1.42,0.0,9.4,0,0,1]);  
         
 case 'SC_MLR_SB_C'	%SurvivalCraft_MaritimeLifeRafts_ShallowBallastsystems_Canopy
   	Target_coef = ([2.68,0.0,12.0,1.10,0.0,9.4,-1.10,0.0,9.4,0,0,1]);
	case 'SC_MLR_SB_C_NoD'	%NoDrogue
    	Target_coef = ([2.96,0.0,1.5,1.21,0.0,1.7,-1.21,0.0,1.7,0,0,1]);
	case 'SC_MLR_SB_C_D'		%Drogue
   	Target_coef = ([2.31,0.0,4.0,0.95,0.0,3.5,-0.95,0.0,3.5,0,0,1]);
	case 'SC_MLR_SB_C_Cap'		% Capsized
   	Target_coef = ([1.68,0.0,2.4,0.24,0.0,2.4,-0.24,0.0,2.4,0,0,1]);        
         
         
case 'SC_MLR_DB_C'	%SurvivalCraft_MaritimeLifeRafts_DeepBallastsystems_Canopy
   Target_coef = ([3.52,-2.5,6.1,0.62,-3.0,3.5,-0.45,-0.2,3.6,2.62,-1,2]);
   
case 'SC_MLR_DB_C_4'	%SurvivalCraft_MaritimeLifeRafts_DeepBallastsystems_Canopy_4to6man
   Target_coef = ([3.50,-1.8,6.4,0.78,-3.6,3.6,-0.47,-0.1,3.9,2.80,-1,2]);
   
  case 'SC_MLR_DB_C_4_NoD'	%WithoutDrogue
   	Target_coef = ([3.75,-2.3,4.4,0.78,-3.6,3.6,-0.47,-0.1,3.9,2.80,-1,2]);
  case 'SC_MLR_DB_C_4_NoD_LL'	%WithoutDrogue_LightLoading
     Target_coef = ([3.75,-2.32,4.51,1.00,-5.31,3.91,-0.47,-0.14,3.91,3.52,-1,2]); 
  case 'SC_MLR_DB_C_4_NoD_HL'	%WithoutDrogue_HeavyLoading
     Target_coef = ([3.59,-1.92,2.56,0.48,-0.16,2.17,-0.48,0.16,2.17,0,0,2]);  
     
  case 'SC_MLR_DB_C_4_D'	%WithDrogue
   	Target_coef = ([1.91,0.9,1.6,0.78,-3.6,3.6,-0.47,-0.1,3.9,2.80,-1,2]);
  case 'SC_MLR_DB_C_4_D_LL'	%WithDrogue_LightLoading
     Target_coef = ([1.95,-0.53,3.59,0.21,1.29,2.15,-0.21,-1.29,2.15,0,0,2]); 
  case 'SC_MLR_DB_C_4_D_HL'	%WithDrogue_HeavyLoading
     Target_coef = ([2.19,-0.96,1.01,1.39,-7.9,1.46,-1.39,7.9,1.46,0,0,2]); 
     
case 'SC_MLR_DB_C_20'	%SurvivalCraft_MaritimeLifeRafts_DeepBallastsystems_Canopy_15-25man
   Target_coef = ([3.68,-4.96,5.37,0.34,-1.85,2.50,-0.49,1.58,2.63,4.13,-1,2]);
  case 'SC_MLR_DB_C_20_NoD_LL'	%WithoutDrogue
   	Target_coef = ([3.93,-3.30,3.01,0.38,-3.33,2.16,-0.59,1.59,2.28,5.07,-1,2]); 
  case 'SC_MLR_DB_C_20_D_HL'	%WithDrogue_HeavyLoading
     Target_coef = ([3.15,-4.49,3.35,0.39,-1.80,2.50,-0.38,2.98,1.64,6.28,1,2]);
     
 case 'SC_MLR_DB_C_Cap'	%SurvivalCraft_MaritimeLifeRafts_DeepBallastsystems_Canopy_Capsized
   Target_coef = ([0.88,0.0,2.5,0.18,0.0,2.4,-0.18,0.0,2.4,0,0,2]);    
 case 'SC_MLR_DB_C_Swmp'	%SurvivalCraft_MaritimeLifeRafts_DeepBallastsystems_Canopy_Swamped
   Target_coef = ([0.99,0.0,2.4,0.14,0.0,2.3,-0.14,0.0,2.3,0,0,2]);
   
   case 'SC_OMSC_LifeCap'	%SurvialCraft_OtherMaritimeSurvivalCraft_LifeCapsule
   Target_coef = ([3.52,0.0,1.9,1.44,0.0,2.0,-1.44,0.0,2.0,0,0,2]); 
case 'SC_OMSC_USCGkit'	%SurvivalCraft_OtherMaritimeSurvivalCraft_USCGsearecusekit
   Target_coef = ([2.48,0.0,3.8,0.32,0.0,3.4,-0.32,0.0,3.4,0,0,2]);
   
case 'SC_ALR_NoB_C_4_NoD'	%SurvialCraft_AviationLifeRafts_NoBallast_Canopy_4-6Person_NoDrogue
   Target_coef = ([3.39,0.0,2.4,1.49,0.0,2.4,-1.49,0.0,2.4,0,0,1]); 
case 'SC_ALR_Evac'	%SurvivalCraft_AviationLifeRafts_Evac/Slide Raft
   Target_coef = ([2.71,0.0,3.8,0.72,0.0,3.4,-0.72,0.0,3.4,0,0,2]); 
   
case 'PPC_Kayak'	%Person-Powered-Craft_SeaKayak
   Target_coef = ([1.16,11.12,4.12,0.41,0.00,4.39,-0.41,0.00,4.39,0,0,2]);
case 'PPC_Surfboard'	%Person-Powered-Craft_Surfboard check D angle
     Target_coef = ([1.93,0.0,8.3,0.51,0.0,6.7,-0.51,0.0,6.7,0,0,2]);    
case 'PPC_WindSurf'	%Person-Powered-Craft_Windsurfer
   Target_coef = ([2.25,5.01,2.50,0.69,-1.30,2.96,-0.69,1.30,2.96,0,0,2]);
   
case 'SV_M_Full_D'	%SailingVessel_Mono-hull_FullKeel_Deep_Draft
   Target_coef = ([2.00,0.0,8.3,2.23,0.0,6.7,-2.23,0.0,6.7,0,0,3]); 
case 'SV_M_Fin_D'	%SailingVessel_Mono-hull_FinKeel_Deep_Draft
   Target_coef = ([2.67,0.0,8.3,2.98,0.0,6.7,-2.98,0.0,6.7,0,0,3]);  
   
case 'PV_S_F'	%PowerVessel_skiffs_Flatbottom
   Target_coef = ([3.15,0.0,2.2,1.29,0.0,2.2,-1.29,0.0,2.2,0,0,1]); 
   
case 'PV_S_V'	%PowerVessel_skiffs_V-hull
   Target_coef = ([2.87,3.98,3.33,0.32,-2.93,2.53,-0.62,1.03,3.05,4.2,-1,2]); 
  case 'PV_S_V_Swmp'	%PowerVessel_skiffs_V-hull_Swamped
    Target_coef = ([1.65,0.0,3.1,0.39,0.0,2.9,-0.39,0.0,2.9,0,0,2]); 
    
case 'PV_SB_CC'	%PowerVessel_SportBoat_CuddyCabin
   Target_coef = ([6.54,0.0,3.0,2.19,0.0,2.8,-2.19,0.0,2.8,0,0,2]);
case 'PV_SF_CC'	%PowerVessel_SportFisher_CenterConsol
   Target_coef = ([5.55,0.0,3.3,2.27,0.0,3.0,-2.27,0.0,3.0,0,0,2]); 

case 'PV_CFV'	%PowerVessel_CommercialFishingVessels
   Target_coef = ([2.47,0.0,12.0,2.76,0.0,9.4,-2.76,0.0,9.4,0,0,3]); 
case 'PV_CFV_samp'	%PowerVessel_CommercialFishingVessels_Sampans
   Target_coef = ([2.67,0.0,8.3,2.98,0.0,6.7,-2.98,0.0,6.7,0,0,3]);  
case 'PV_CFV_trol'	%PowerVessel_CommercialFishingVessels_Troller
   Target_coef = ([2.80,0.0,8.3,3.13,0.0,6.7,-3.13,0.0,6.7,0,0,3]);    
case 'PV_CFV_long'	%PowerVessel_CommercialFishingVessels_Longliner
   Target_coef = ([2.47,0.0,8.3,2.76,0.0,6.7,-2.76,0.0,6.7,0,0,3]);    
case 'PV_CFV_junk'	%PowerVessel_CommercialFishingVessels_Korean
   Target_coef = ([1.80,0.0,3.79,2.01,0.0,3.3,-2.01,0.0,3.3,0,0,3]);
   
case 'PV_CFV_gill'	%PowerVessel_CommercialFishingVessels_Gill-netter
    Target_coef = ([3.72,-0.87,3.33,1.41,2.00,3.36,-1.41,-2.00,3.36,0,0,3]);
   
case 'PV_CF'	%PowerVessel_CoastalFreighter
   Target_coef = ([1.87,0.0,8.3,2.09,0.0,6.7,-2.09,0.0,6.7,0,0,3]);
   
case 'BD_FV'	%BoatingDebris_FishingVessel
   Target_coef = ([1.97,0.0,8.3,0.36,0.0,6.7,-0.36,0.0,6.7,0,0,3]); 
case 'BD_bait'	%BoatingDebris_Baitbox			 
     Target_coef = ([0.72,15.18,5.59,1.86,-5.26,4.20,-1.86,5.26,4.20,0,0,1]);
	case 'BD_bait_LL'	%BoatingDebris_Baitbox_LightlyLoaded	 
   	Target_coef = ([2.53,9.01,3.05,1.09,-2.76,4.14,-1.09,2.76,4.14,0,0,2]);
   case 'BD_bait_HL'	%BoatingDebris_Baitbox_FullyLoaded	
      Target_coef = ([1.15,7.94,3.17,1.48,-0.32,2.99,-1.48,0.32,2.99,0,0,2]);
        
         
case 'NoSAR_IV_RR_NoS'	%Non-SARobjects_ImmigrationVessel_RefugeeRaft_NoSail
   	Target_coef = ([1.56,8.30,1.53,0.078,2.70,1.52,-0.078,-2.70,1.52,0,0,2]);  				     
case 'NoSAR_IV_RR_S'	%Non-SARobjects_ImmigrationVessel_RefugeeRaft_Sail 
     Target_coef = ([6.43,-3.47,3.63,2.22,0.00,7.12,-2.22,0.00,7.12,0,0,2]);  
     % USED Constrained regression for abs +/-CWL
     
case 'NoSAR_SF_tamp'	%Non-SARobjects_SewageFloatables_TamponApplicators
   Target_coef = ([1.79,0.0,3.1,0.16,0.0,2.9,-0.16,0.0,2.9,0,0,1]);  
   
case 'NoSAR_MW'	%Non-SARobjects_MedicalWaste
   Target_coef = ([2.75,0.0,12.0,0.50,0.0,9.4,-0.50,0.0,9.4,0,0,1]);   
  case 'NoSAR_MW_V'	%Non-SARobjects_MedicalWaste_Vials
   	Target_coef = ([3.64,0.0,12.0,0.67,0.0,9.4,-0.67,0.0,9.4,0,0,1]); 
   case 'NoSAR_MW_V_L'	%Non-SARobjects_MedicalWaste_Vials_Large
   	Target_coef = ([4.34,0.0,3.1,0.74,0.0,2.9,-0.74,0.0,2.9,0,0,1]);
   case 'NoSAR_MW_V_S'	%Non-SARobjects_MedicalWaste_Vials_Small
      Target_coef = ([2.95,0.0,5.4,0.54,0.0,4.5,-0.54,0.0,4.5,0,0,1]);
  case 'NoSAR_MW_S'	%Non-SARobjects_MedicalWaste_Syringes
   	Target_coef = ([1.79,0.0,12.0,0.16,0.0,9.4,-0.16,0.0,9.4,0,0,1]); 
   case 'NoSAR_MW_S_L'	%Non-SARobjects_MedicalWaste_Syrines_Large
   	Target_coef = ([1.79,0.0,3.1,0.16,0.0,2.9,-0.16,0.0,2.9,0,0,1]);
   case 'NoSAR_MW_S_S'	%Non-SARobjects_MedicalWaste_Syrines_Small
      Target_coef = ([1.79,0.0,2.4,0.16,0.0,2.3,-0.16,0.0,2.3,0,0,1]);   
      
     
     
otherwise
   disp('Target not avialable, request more funding for ISARC Leeway Studies')
   Target_coef = ([]);
end

   
   