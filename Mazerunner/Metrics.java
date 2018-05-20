import java.util.HashMap;

class Metrics {
		public long i_count;
		public long b_count;
		public int m_count;

		public HashMap<String,Long> instrucTypes = new HashMap<String, Long>();

		public Metrics(int i_count, int b_count, int m_count,int facc_count,int memacc_count){
			this.i_count = i_count;
			this.b_count = b_count;
			this.m_count = m_count;

			for(String it : BIT.highBIT.InstructionTable.InstructionTypeName) {
				if (it.equals("CLASS_INSTRUCTION")||it.equals("INSTRUCTIONCHECK_INSTRUCTION") || it.equals("COMPARISON_INSTRUCTION") ) {
					instrucTypes.put(it, new Long(0));
				}
			}
		}
	}