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
	     	 int size = new_friends.size();
	     	 if(size < max_suggestions){
	     		 new_friends.add(new Suggestion(Integer.parseInt(friend_id),mutual));
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
	     		 if(index != -1) new_friends.set(index,(new Suggestion(Integer.parseInt(friend_id),mutual)));
	     	 }
	    }
   
	   @Override
	   public String toString(){
    	  String str = "";
    	  for(int i = 0; i < new_friends.size(); i++){
    		  Suggestion sug = new_friends.get(i);
    		  str = str + sug.get_id() + ",";
    	  }
    	  return str;
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
		 int i = Integer.parseInt(id);
		 int num_users = 50000;
		 FriendSuggestions list = new FriendSuggestions();
		 HashSet<String> my_friends = friends.get(id);
		 for(int j = 0; j < num_users; j++){
			 String other = Integer.toString(j);
			 if(i!=j && !my_friends.contains(other)){
				 HashSet<String> their_friends = friends.get(other);
				 int numIntersection = intersection(my_friends,their_friends);
				 list.add_mutual(other,numIntersection);
			 }
		 }
		 String ordered_list = list.toString();
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