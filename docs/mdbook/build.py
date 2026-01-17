#  SPDX-License-Identifier: GPL-3.0-or-later

import re
import os
import argparse

def sanitize_filename(title):
    """Convert a title to a valid filename."""
    # Remove characters that aren't suitable for filenames
    sanitized = re.sub(r'[^\w\s-]', '', title.lower())
    # Replace spaces with hyphens
    sanitized = re.sub(r'\s+', '-', sanitized)
    return sanitized

def extract_heading_level(line):
    """Extract heading level (1-4) from markdown heading."""
    match = re.match(r'^(#{1,3})\s+(.+)$', line)
    if match:
        return len(match.group(1)), match.group(2).strip()
    return 0, None

def process_markdown_file(input_file, output_dir):
    """Split markdown file into sections based on headings and generate summary."""
    os.makedirs(output_dir, exist_ok=True)

    with open(input_file, 'r', encoding='utf-8') as f:
        content = f.read()

    # Split content by lines
    lines = content.split('\n')

    current_files = {}  # Level -> current filename for that level
    file_contents = {}  # Filename -> content
    structure = []  # For building the summary structure

    # Track used filenames to avoid duplicates
    used_filenames = {}  # Base filename -> count

    current_content = []
    current_level = 0
    current_title = "index"

    # Hierarchical numbering to ensure unique files
    section_numbers = {1: 0, 2: 0, 3: 0, 4: 0}

    for line in lines:
        level, title = extract_heading_level(line)

        if level > 0:  # This is a heading
            # Save current content before starting a new section
            if current_content:
                # Generate a unique filename
                base_filename = sanitize_filename(current_title)

                # Update section numbers for hierarchy
                section_numbers[current_level] += 1
                # Reset all lower levels
                for l in range(current_level + 1, 5):
                    section_numbers[l] = 0

                # Create a unique section identifier
                section_id = ""
                for l in range(1, current_level + 1):
                    section_id += f"{section_numbers[l]}."

                # Create unique filename with section numbers
                filename = f"{section_id.rstrip('.')}-{base_filename}.md"

                current_files[current_level] = filename

                # Add to structure
                indent = "  " * (current_level - 1)
                structure.append(f"{indent}- [{current_title}]({filename})")

                file_contents[filename] = '\n'.join(current_content)

            # Reset content for new section
            current_content = [line]
            current_level = level
            current_title = title

            # Clear lower level files
            for l in list(current_files.keys()):
                if l > level:
                    del current_files[l]
        else:
            # Add to current content
            current_content.append(line)

    # Save the last section
    if current_content:
        # Generate a unique filename
        base_filename = sanitize_filename(current_title)

        # Update section numbers
        section_numbers[current_level] += 1

        # Create a unique section identifier
        section_id = ""
        for l in range(1, current_level + 1):
            section_id += f"{section_numbers[l]}."

        # Create unique filename with section numbers
        filename = f"{section_id.rstrip('.')}-{base_filename}.md"

        current_files[current_level] = filename

        # Add to structure
        indent = "  " * (current_level - 1)
        structure.append(f"{indent}- [{current_title}]({filename})")

        file_contents[filename] = '\n'.join(current_content)

    # Write files
    for filename, content in file_contents.items():
        with open(os.path.join(output_dir, filename), 'w', encoding='utf-8') as f:
            f.write(content)

    # Create SUMMARY.md
    summary_content = "# Summary\n\n" + "\n".join(structure)
    with open(os.path.join(output_dir, "SUMMARY.md"), 'w', encoding='utf-8') as f:
        f.write(summary_content)

    return structure

def convert_latex_to_markdown(input_file, output_file):
    """Convert LaTeX to Markdown using Pandoc."""
    import subprocess

    cmd = ["pandoc", "-f", "latex", "-t", "markdown_strict+tex_math_dollars",
           input_file, "-o", output_file]

    subprocess.run(cmd, check=True)
    return output_file

def main():
    parser = argparse.ArgumentParser(description="Convert LaTeX to mdBook format")
    parser.add_argument("input_file", help="Input LaTeX file")
    parser.add_argument("--output-dir", default="src",
                        help="Output directory for mdBook source files")
    parser.add_argument("--skip-pandoc", action="store_true",
                        help="Skip Pandoc conversion (input is already Markdown)")

    args = parser.parse_args()

    # Step 1: Convert LaTeX to Markdown if needed
    if not args.skip_pandoc:
        print(f"Converting {args.input_file} to Markdown...")
        temp_md = "temp_converted.md"
        convert_latex_to_markdown(args.input_file, temp_md)
        input_md = temp_md
    else:
        input_md = args.input_file

    # Step 2: Process Markdown and split into sections
    print(f"Processing Markdown and creating mdBook structure in {args.output_dir}...")
    structure = process_markdown_file(input_md, args.output_dir)

    # Cleanup temporary file
    if not args.skip_pandoc and os.path.exists(temp_md):
        os.remove(temp_md)

    print(f"Done! mdBook structure created in {args.output_dir}")
    print(f"Created {len(structure)} section files")
    print("You can now run 'mdbook build' in the parent directory of your src folder")

if __name__ == "__main__":
    main()