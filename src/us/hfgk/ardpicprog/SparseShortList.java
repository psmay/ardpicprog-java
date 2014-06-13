package us.hfgk.ardpicprog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class SparseShortList {
	private static class Block {
		private int startIndex;
		private short[] data;

		public Block(int startIndex, int minCapacity, short... data) {
			this.startIndex = startIndex;
			if (data.length > minCapacity)
				minCapacity = data.length;
			this.data = Arrays.copyOf(data, minCapacity);
		}

		private void resizeLeftAligned(int size) {
			this.recentRange = null;
			this.data = Arrays.copyOf(data, size);
		}

		private void resizeRightAligned(int size) {
			this.recentRange = null;
			this.data = Common.rightCopyOf(data, size);
		}

		public void append(short word) {
			resizeLeftAligned(data.length + 1);
			data[data.length - 1] = word;
		}

		public void prepend(short word) {
			resizeRightAligned(data.length + 1);
			--startIndex;
			data[0] = word;
		}
		
		private IntRange recentRange = null;
		
		public IntRange currentRange() {
			if(recentRange == null) {
				recentRange = IntRange.getSize(startIndex, data.length);
			}
			return recentRange;
		}
	}

	private ArrayList<Block> blocks = new ArrayList<Block>();

	void clear() {
		blocks.clear();
	}

	private void insertBlock(Block block) {
		int index = findInsertIndex(block.startIndex);
		if (index >= 0)
			blocks.add(index, block);
		else
			blocks.add(block);
	}

	private int findInsertIndex(int address) {
		int index = -1;
		for (Block block : blocks) {
			++index;
			if (address <= block.startIndex) {
				return index;
			}
		}
		return -1;
	}

	List<IntRange> extents() {
		ArrayList<IntRange> result = new ArrayList<IntRange>();
		for (Block block : blocks) {
			result.add(block.currentRange());
		}
		return result;
	}
	
	short get(int index, short defaultValue) {
		Block block = getContainingBlock(index);
		return (block != null) ? block.data[index - block.startIndex] : defaultValue;
	}
	
	Short get(int index) {
		Block block = getContainingBlock(index);
		return (block != null) ? block.data[index - block.startIndex] : null;
	}

	private final Block getContainingBlock(int index) {
		for (Block block : blocks) {
			if (index >= block.startIndex && index < (block.startIndex + block.data.length)) {
				return block;				
			}
		}
		return null;
	}

	void set(int address, short word) {
		int index = -1;

		for (Block block : blocks) {
			++index;

			if (address < block.startIndex) {
				if (address == (block.startIndex - 1)) {
					// Prepend to the existing block.
					block.prepend(word);
				} else {
					// Create a new block before this one.
					blocks.add(index, new Block(address, 0, word));
				}
				return;
			} else if (address < (block.startIndex + block.data.length)) {
				// Update a word in an existing block.
				block.data[address - block.startIndex] = word;
				return;
			} else if (address == (block.startIndex + block.data.length)) {
				// Can we extend the current block without hitting the next
				// block?
				if (index < (blocks.size() - 1)) {
					Block next = blocks.get(index + 1);
					if (address < next.startIndex) {
						block.append(word);
						return;
					}
				} else {
					block.append(word);
					return;
				}
			}
		}

		blocks.add(new Block(address, 0, word));
	}

	void readBlock(BlockReader br, IntRange range) throws IOException {
		Block block = new Block(range.start(), range.size());
		br.doRead(range, block.data, 0);
		insertBlock(block);
	}

	int writeBlock(BlockWriter bw, IntRange range) throws IOException {
		int count = 0;
		for (Block block : blocks) {
			IntRange blockRange = block.currentRange();
			if (range.containsRange(blockRange)) {
				int offset = 0;

				int overlapStart;
				int overlapPost;
				
				if (range.start() > blockRange.start()) {
					offset += (range.start() - blockRange.start());
					overlapStart = range.start();
				} else {
					overlapStart = blockRange.start();
				}
				if (range.post() < blockRange.post())
					overlapPost = range.post();
				else
					overlapPost = blockRange.post();
				IntRange overlap = IntRange.getPost(overlapStart, overlapPost);
				bw.doWrite(overlap, block.data, offset);
				count += overlapPost - overlapStart;
			}
		}
		return count;
	}

	interface BlockWriter {
		public void doWrite(IntRange range, short[] data, int offset) throws IOException;
	}

	interface BlockReader {
		public void doRead(IntRange range, short[] data, int offset) throws IOException;
	}
}
