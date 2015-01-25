package edu.stanford.cs246.mutualfriend;

import java.io.IOException;
import java.util.Arrays;
import java.util.*;
import java.util.HashMap;
import java.lang.StringBuffer;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

public class MutualFriend extends Configured implements Tool {
	public static HashMap<String,HashSet<String>> friends;
	
   public static void main(String[] args) throws Exception {
      System.out.println(Arrays.toString(args));
      //Initialize our info
      int num_users = 50000;
      friends = new HashMap<String,HashSet<String>>(num_users);
      int res = ToolRunner.run(new Configuration(), new MutualFriend(), args);
      ///
      System.exit(res);
   }

   @Override
   public int run(String[] args) throws Exception {
      System.out.println(Arrays.toString(args));
      Job job = new Job(getConf(), "MutualFriend");
      job.setJarByClass(MutualFriend.class);
      job.setOutputKeyClass(Text.class);
      job.setOutputValueClass(Text.class);

      job.setMapperClass(Map.class);
      job.setReducerClass(Reduce.class);

      job.setInputFormatClass(TextInputFormat.class);
      job.setOutputFormatClass(TextOutputFormat.class);

      FileInputFormat.addInputPath(job, new Path(args[0]));
      FileOutputFormat.setOutputPath(job, new Path(args[1]));

      job.waitForCompletion(true);
      
      return 0;
   }
   
   public static class Suggestion{
	   private int mutual_friends;
	   private int user_id;
	   
	   public Suggestion(int user_id,int mutual_friends){
		   this.user_id = user_id;
		   this.mutual_friends = mutual_friends;
	   }
	   
	   public int get_id(){
		   return user_id;
	   }
	   
	   public int get_mutual(){
		   return mutual_friends;
	   }
	   
	   @Override
	   public boolean equals(Object obj){
		   if(obj == null) return false;
		   if(!(obj instanceof Suggestion)) return false;
		   if(obj == this) return true;
		   Suggestion b = (Suggestion)obj;
		   if(this.get_id() == b.get_id()) return true;
		   return false;
	   }
   }
   
   public static class FriendSuggestions{
	   private static int max_suggestions = 10;
	   private ArrayList<Suggestion> new_friends;
		 
	   public FriendSuggestions(){
		   new_friends = new ArrayList<Suggestion>();
	   }
	   
	   public Suggestion get(int index){
		   return new_friends.get(index);
	   }
	   
	   public int size(){
		   return new_friends.size();
	   }
	   
	   public void add_mutual(String friend_id, int mutual){
		   	 if(mutual == 0) return;
		   	 Suggestion add_sug = new Suggestion(Integer.parseInt(friend_id),mutual);
		   	 if(new_friends.contains(add_sug)) return;
	     	 int size = new_friends.size();
	     	 if(size < max_suggestions){
	     		 new_friends.add(add_sug);
	     	 }else{
	     		 //Should probably keep track of max and min suggestions
	     		 int min = Integer.MAX_VALUE;
	     		 int min_id = 0;
	     		 int index = -1;
	     		 for(int i = 0; i < size; i++){
	     			 Suggestion sug = new_friends.get(i);
	     			 if(sug.get_mutual() < min){
	     				 min = sug.get_mutual();
	     				 min_id = sug.get_id();
	     				 index = i;
	     			 }
	     			 if(sug.get_mutual() == min){
	     				 //Need to keep the lower id
	     				 if(sug.get_id() > min_id){
	     					 min_id = sug.get_id();
	     					 index = i;
	     				 }
	     			 }
	     		 }
	     		 if(mutual > min){
	     			 new_friends.set(index,add_sug);
	     		 }
	     		 if(mutual == min){
	     			 if(add_sug.get_id() > min_id) new_friends.set(index,add_sug);
	     		 }
	     	 }
	    }
   
	   @Override
	   public String toString(){
		  Collections.sort(new_friends,new SuggestionComparator());
    	  String str = "";
    	  for(int i = 0; i < new_friends.size(); i++){
    		  Suggestion sug = new_friends.get(i);
    		  if(i+1 != new_friends.size()){
    			  str = str + sug.get_id() + ",";
    		  }else{
    			  str = str + sug.get_id();
    		  }
    	  }
    	  return str;
	   }
   }
   
   public static class SuggestionComparator implements Comparator<Suggestion>{
	   	@Override
	    public int compare(Suggestion a, Suggestion b) {
	   		int a_mut = a.get_mutual();
	   		int b_mut = b.get_mutual();
	        if(a_mut < b_mut){
	            return 1;
	        }
	        if(a_mut > b_mut){
	            return -1;
	        }else{
	        	//They have equal mutual friends
	        	if(a.get_id() < b.get_id()){
	        		return 1;
	        	}else{
	        		return -1;
	        	}
	        }
	    }
   }
   
   public static class Map extends Mapper<LongWritable, Text, Text, Text> {
	  private Text key_user_id = new Text();
      private Text value_user_id = new Text();

      @Override
      public void map(LongWritable key, Text value, Context context)
              throws IOException, InterruptedException {
    	  value_user_id.set("");
		  String[] contents = value.toString().split("\\s+");
		  int size = contents.length;
		 //This populates each users friend list
		 for(int i = 0; i < size-1; i=i+2) {
			 String user_id = contents[i];
			 String friends_list = contents[i+1];
			 set_friends(user_id,friends_list.split(","));
			 key_user_id.set(user_id);
			 context.write(key_user_id,value_user_id);
		 }
      }
      
     private void set_friends(String user_id, String[] separated_list){
    	 HashSet<String> friends_ar = new HashSet<String>();
    	 for(int j = 0; j < separated_list.length; j++){
    		 friends_ar.add(separated_list[j]);
    	 }
    	 friends.put(user_id,friends_ar);
     }
     
   }

   public static class Reduce extends Reducer<Text, Text, Text, Text> {
	   private Text user_list = new Text();
	   int max_suggestions = 10;
	   
      @Override
      public void reduce(Text key, Iterable<Text> values, Context context)
              throws IOException, InterruptedException {        
         //This creates the sets of mutual friends
         String id = key.toString();
		 FriendSuggestions list = new FriendSuggestions();
		 HashSet<String> my_friends = friends.get(id);
		 //For each one of my friends
		 for(String other: my_friends){
			 //I get their friends
			 HashSet<String> their_friends = friends.get(other);
			 //For each one of their friends
			 for(String degree: their_friends){
				 //If not myself and we're not already friends
				 if(!id.equals(degree) && !my_friends.contains(degree)){
					 //Find our mutual friends
					 int numIntersection = intersection(my_friends,friends.get(degree));
					 list.add_mutual(degree,numIntersection);
				 }
			 }
		 }
		 String ordered_list = list.toString();
		 //System.out.println(ordered_list);
		 user_list.set(ordered_list);
         context.write(key, user_list);
      }
      
      private int intersection(HashSet<String> a, HashSet<String> b){
    	 if(a == null || b == null) return 0;
     	 HashSet<String> c = new HashSet<String>(a);
     	 c.retainAll(b);
     	 return c.size();
      }
    
   }
}
