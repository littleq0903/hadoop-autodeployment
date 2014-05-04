default_target: deb
all: deb
deb: hbase.deb

hbase.deb: md5sums conffiles
	dpkg-deb --build  src/ hbase.deb

md5sums:
	cd src && find . -type f ! -regex '.*.hg.*' ! -regex '.*?debian-binary.*' ! -regex '.*?DEBIAN.*' -printf '%P ' | xargs md5sum > DEBIAN/md5sums

conffiles:
	cd src && find etc/hbase/ -type f -printf '/%p\n' > DEBIAN/conffiles

clean:
	rm hbase.deb

