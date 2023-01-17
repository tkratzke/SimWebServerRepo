function Percent_change = JibingModel(jibing_type,Wind_Speed,Delta_wind_dir);
% Percent_change = JibingModel(jibing_type,Wind_Speed,Delta_wind_dir);
% Wind_Speed in m/s
% jibing_type = 1 low,2 mod. or 3 high resistance to jibing
% Delta_wind_dir is wind_dir(t) - wind_dir(t-1) where time interal is taken to be 1 hour
% This model matches "Leeway Divergence" model, except that intergers are outputted.
% Art Allen 860-441-2747 

 Lower_Wind_Speed_threshold = jibing_type + 2;  % 3, 4, or 5 m/s
 Upper_Wind_Speed_threshold = 5.*jibing_type + 5; % 10,15,or 20 m/s
 
 % CHECK to see if Delta_wind_dir is real or NaN (Not a Number)
 % Delta_wind_dir will be NaN when a wind data is missing.
 if isnan(Delta_wind_dir)  
    Percent_change = 0;
 	else
 % Delta_wind_dir is real     

if abs(Delta_wind_dir) < 30  % When Delta Wind Direction is less than 30 degrees 
   
	if Wind_Speed <= Lower_Wind_Speed_threshold; % Jib at Low wind speed
   	Percent_change = round(Lower_Wind_Speed_threshold  - Wind_Speed);
   elseif Wind_Speed > Upper_Wind_Speed_threshold; %Jib at High wind speed
      Percent_change = floor(0.5.*Wind_Speed - Upper_Wind_Speed_threshold./2); 
   elseif Wind_Speed >Lower_Wind_Speed_threshold & Wind_Speed <= Upper_Wind_Speed_threshold %No jibing Between the min and max.
     Percent_change = 0;
  end
  
else	% When Delta Wind Direction is equal or greater than 30 degrees 
   Percent_change_wind_dir = round(abs(Delta_wind_dir./4)); % Jibing due to wind shift
  
   	if Wind_Speed <= Lower_Wind_Speed_threshold; % Jib at Low wind speed
   	Percent_change = round(Lower_Wind_Speed_threshold - Wind_Speed) + Percent_change_wind_dir;
   elseif Wind_Speed > Upper_Wind_Speed_threshold	%Jib at High wind speed
      Percent_change = floor(0.5.*Wind_Speed - Upper_Wind_Speed_threshold./2)+ Percent_change_wind_dir; 
   elseif Wind_Speed > Lower_Wind_Speed_threshold & Wind_Speed <= Upper_Wind_Speed_threshold
     Percent_change = 0 + Percent_change_wind_dir; % Jibing due to wind shift only
  end
  
end

% The maximun affect occurs when half of the replications jib.
	if Percent_change > 50
   	Percent_change = 50;
   end

end
