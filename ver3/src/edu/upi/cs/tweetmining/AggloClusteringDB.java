package edu.upi.cs.tweetmining;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.logging.Level;
import java.util.logging.Logger;


public class AggloClusteringDB {
/*
 *   Agglomerative clustering (biner)
 *   
 *   tabel input: 
 *   - twjadi hasil dari PrepoTwMentahDB
 *   - tfidf hasil  dari TfidfDB
 *   
 *   output ke file teks (dalam format html)
 *   - tbd: output ke table juga??   
 *    
 *   note: cek querynya, mungkin menggunakan limit
 *   
 */
	
	
	public String dbName;
    public String userName;
    public String password;
    public String namaFileOutput;
    public String tableNameTwJadi="tw_jadi";  //default
    public String tableNameTfidf ="tfidf";
	
	private class JarakCluster  {
		public int i;
		public int j;
		double jarak; 
		public JarakCluster(int vi,int vj,double vJarak) {
			i = vi;
			j = vj;
			jarak = vJarak;
		}
	}
	
	//berdasarkan JarakCluster
    public class JarakClusterComparable implements Comparator<JarakCluster>{
        @Override
        public int compare(JarakCluster o1, JarakCluster o2) {
                return (o1.jarak>o2.jarak ? -1 : (o1.jarak==o2.jarak ? 0 : 1));
        }
    }
    
    //berdasarkan kohesi
    public class CohesionClusterComparable implements Comparator<ClusterAgglo>{
        @Override
        public int compare(ClusterAgglo o1, ClusterAgglo o2) {
            double coh1 = o1.cohesion();     
            double coh2 = o2.cohesion();
        	return (coh1>coh2 ? -1 : (coh1==coh2 ? 0 : 1));
        }
    }
    
    //berdasarkan inner quality
    public class InnerQClusterComparable implements Comparator<ClusterAgglo>{
        @Override
        public int compare(ClusterAgglo o1, ClusterAgglo o2) {
            double coh1 = o1.innerQualityScore();     
            double coh2 = o2.innerQualityScore();
        	return (coh1>coh2 ? -1 : (coh1==coh2 ? 0 : 1));
        }
    }

    
    private ArrayList<ClusterAgglo> alAllCluster = new ArrayList<ClusterAgglo>();  //semua cluster dalam tree
	
	public void process() {
		//menggunakan LIMIT, cek dulu!!
		//mulai dari jumlah cluster sebanyak data
		//loop untuk semua cluster, cari cluster yang jarak antar keduanya terpendek, gabung
		//kondisi berhenti jika sisa cluster=2
		
		
		//load data tweet 
		System.out.println("AggloClustering");
        System.out.println("Lab Basdat Ilkom UPI (cs.upi.edu)");
        System.out.println("=================================");
        //ambil data, pindahkan ke memori
        Logger log = Logger.getLogger("edu.cs.upi.kmeans");


        ArrayList<DocKMeans> alTweet = new ArrayList<DocKMeans>();            //kumpulan tweet
        
        Connection conn=null;       
        PreparedStatement pTw = null;
        try {         
        	Class.forName("com.mysql.jdbc.Driver");            
            String strCon = "jdbc:mysql://"+dbName+"?user="+userName+"&password="+password;
            System.out.println(strCon);
            conn = DriverManager.getConnection(strCon);
            
            //nanti limitnya dimatikan?
            //data yang duplikat tidak diproses
            //ambil tweet original karena untuk perhitungan yg digunakan adalah tfidf
            
            String q = "select  concat('(',tj.id_internal,')',tj.text) as tw,t.tfidf_val as tfidf "+
            		   "from "+ tableNameTfidf +" t,"+ tableNameTwJadi + " tj " + 
            		   "where t.id_internal_tw_jadi = tj.id_internal and trim(t.tfidf_val)<>'' and tj.is_duplicate=0 "+
            		   "limit 0,1000";
            
            pTw  =  conn.prepareStatement (q);

            ResultSet rsTw = pTw.executeQuery();
//            int cc=0;
            while (rsTw.next())   {  
//                cc++;
            	String strTw     = rsTw.getString(1);  //tweet
            	String strTfidf  = rsTw.getString(2);   //tfidf
                DocKMeans tweet = new DocKMeans();
                tweet.text = strTw;
                tweet.addTermsInLine(strTfidf);
                //System.out.println("-->"+tweet.text);
                alTweet.add(tweet);
            }

        } catch (Exception e) {
            log.severe(e.toString());
        }
        finally {
            try {
                pTw.close();
                conn.close();
            } catch (Exception e) {
                log.log(Level.SEVERE, null, e);
            }    
        }
        //fs: tweet sudah masuk ke array
        
        
        long level=-99;
     	double lg = Math.log(alTweet.size()) / Math.log(2);
     	if (lg % 1.0 > 0) {  //ada pecahan, 
     			level = (long) (Math.floor(lg)+2);
     	} else if (lg % 1.0 == 0) {
     			level =(long) ( lg+1);
     	}
     	long maxLevel = level; 
     		
     	System.out.println("Jum data:"+alTweet.size());
     	System.out.println("Level:"+level);
        
        ArrayList<ClusterAgglo> alCluster = new ArrayList<ClusterAgglo>();  //kumpulan cluster per level
        
        //insialisasi cluster, satu cluster satu tweet
        DocKMeans t;
		for (int i=0;i<alTweet.size();i++) {
			ClusterAgglo c = new ClusterAgglo(i);
			t = alTweet.get(i);
			c.addDoc(t);
			c.calcCentroid();
			c.level = level;
	        alCluster.add(c);
	        alAllCluster.add(c);
		}
		

		//loop, sampai jumlah cluster =1
			//cari pasangan tweet yang paling dekat
			//buat matrix jarak
			//jarak[a,b] 
			//cari a,b yang paling kecil jaraknya dimana a<>b
			//gabung a,b
			//jumlah cluster akan berkurang separuh
		
		
	 
		
		long incID= 0;
			
		
		while (alCluster.size()>1) {
			level--;
			//hitung semua jarak antara cluster 
			ArrayList<JarakCluster> alJarakCluster = new ArrayList<JarakCluster>();  //kumpulan jarak cluster
			ClusterAgglo c1,c2;
			for (int i=0;i<alCluster.size();i++) {
				c1 = alCluster.get(i);
				for (int j=i;j<alCluster.size();j++) {
					if (i!=j) {
						c2 = alCluster.get(j);
						double jarakcluster = c1.calcJarakAntarCluster(c2);
						alJarakCluster.add(new JarakCluster(i,j,jarakcluster));
					}
				}
			}
			Collections.sort(alJarakCluster, new JarakClusterComparable()); //sort berdasarkan terpendek (1:paling pendek, 0: paling jauh)
			
			
			//penggabungan cluster yang terdekat
			JarakCluster jc;
			ClusterAgglo ca,cb;
			ArrayList<ClusterAgglo> alClusterBaru = new ArrayList<ClusterAgglo>();
//			int cc=0;
			for (int i=0;i<alJarakCluster.size();i++) {    //sudah terurut, bisa digabung dari yg terkecil
				jc = alJarakCluster.get(i);
				ca = alCluster.get(jc.i);
				cb = alCluster.get(jc.j);
				//gabung cluster, tandai agar tidak digabung lagi
				if (!ca.flag&&!cb.flag) {
					//System.out.println("Gabung "+jc.i+" dengan "+jc.j+" dengan jarak:"+jc.jarak);
					//ca.print();
					//cb.print();
					//System.out.println("-------------");
					ca.flag = true;
					cb.flag = true;
					ClusterAgglo cGab = new ClusterAgglo(incID*1000+ca.idCluster);
					cGab.level = level;
					cGab.mergeCluster(ca);
					cGab.mergeCluster(cb);  
					cGab.calcCentroid();
					ca.parent = cGab;
					cb.parent = cGab;
					cGab.addChild(ca);
					cGab.addChild(cb);
					alClusterBaru.add(cGab);
					alAllCluster.add(cGab);
					//cc++;
					//System.out.println(cc);
				}
	//debug
	//			System.out.println(ca.centroid.toString());
	//			System.out.println("++");
	//			System.out.println(cb.centroid.toString());
	//			System.out.println(jc.jarak);
	//			System.out.println("------");
			} //end for
			//yang sisa belum bergabung dimasukkan
			for (ClusterAgglo c:alCluster) {
				if (!c.flag) {
//					System.out.println("tdk punya pasangan:");
//					c.print();
					//tambah ke cluster baru
					//level cluster tidak berubah
					alClusterBaru.add(c);
				}
			}
			alCluster = alClusterBaru;	
			incID++;
		} //end loop
		//alCluster berisi cluster paling atas
		//print rekursif
		
		//beri label
		
		//beriLabel(alCluster,"1");
		//printTreeMedoid(alCluster,"",1);
		//printTree(alCluster);
		
		
		//
		
		
        //tampilkan cluster berdasarkan kohesinya 		
		try {
			PrintWriter pw = new PrintWriter(namaFileOutput);
			//Collections.sort(alAllCluster, new CohesionClusterComparable()); //sort berdasarkan terpendek (1:paling pendek, 0: paling jauh)
			
			Collections.sort(alAllCluster, new InnerQClusterComparable()); //sort quality 
			double sumK=0;
			for  (int i=0;i<alAllCluster.size();i++) {
				ClusterAgglo c = alAllCluster.get(i);
				c.maxLevel = maxLevel;
				pw.println("Kohesi="+c.cohesion()+" ");
				pw.println("Clus Level="+c.level +" ");
				pw.println("innerq="+c.innerQualityScore());
				sumK = sumK + c.cohesion();
				pw.println("Clus ID="+c.idCluster+" ");
				pw.println(c.getMedoid());
				pw.println();
			}
			double avgK = sumK/alAllCluster.size();
			pw.println("Rata2 kohesi:"+avgK);
			pw.close();
		}//try	
		catch(Exception e) {
			log.severe(e.toString());
		}
		
//		//tampilkan dalam bentuk hirarki
//		try {
//			PrintWriter pw = new PrintWriter(namaFileOutput);
//			pw.println("<html>");
//			pw.println("<ul>");
//			printTreeHTML(alCluster.get(0),pw); //cluster paling atas hanya ada satu
//			pw.println("</ul>");
//			pw.println("</html>");
//			pw.close();
//		}
//		catch (Exception e) {
//			log.severe(e.toString());
//		}
	}
	
	public void printTreeHTML(ClusterAgglo c, PrintWriter pw) {		
		
		//print 
//		if (c.parent!=null) {
//			System.out.println("idParent:"+c.parent.idCluster);
//		} else {
//			System.out.println("idParent: <none>  ROOT");
//		}
		pw.println("<li>");
		pw.println("idcluster="+c.idCluster);
		pw.println(";Level"+c.level+";Kohesi="+c.cohesion()+";");
		pw.println(c.getMedoid());
		pw.println("</li>");
		
		//print  anak
		if (c.child.size()>0)  //punya anak
		{
			pw.println("<ul>");
			for (ClusterAgglo ch:c.child) {
				printTreeHTML(ch,pw);			
			}
			pw.println("</ul>");
		}
	}
	

	
	public void printTree(ArrayList<ClusterAgglo> alCluster) {
		for (ClusterAgglo c: alCluster) {
			if (c.parent!=null) {
				System.out.println("idParent:"+c.parent.idCluster);
			}
			c.print();
			System.out.println("anak-->");
			if (c.child!=null) {
				printTree(c.child);
			} 		
		}
	}
	
	
	public static void main(String[] args) {
		AggloClusteringDB aggC= new AggloClusteringDB();
		aggC.dbName="localhost/obama2";
		aggC.userName="yudi3";
		aggC.password="rahasia";
		aggC.tableNameTwJadi="tw_jadi";
		aggC.tableNameTfidf = "tfidf_2000";
		aggC.namaFileOutput = "D:\\xampp\\htdocs\\obama\\obama_2000_kohesi.htm";
		//aggC.tableNameTwJadi ="tw_jadi_sandyhoax_nodup_dukungan";
		aggC.process();
	}
}
